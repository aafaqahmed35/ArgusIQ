package com.argusiq.tracing.service;

import com.argusiq.tracing.dto.DependencyEdgeDto;
import com.argusiq.tracing.dto.DependencyGraphResponse;
import com.argusiq.tracing.dto.OperationMetricDto;
import com.argusiq.tracing.dto.ServiceResponse;
import com.argusiq.tracing.entity.MonitoredService;
import com.argusiq.tracing.repository.MonitoredServiceRepository;
import com.argusiq.tracing.repository.SpanRepository;
import com.argusiq.tracing.repository.TelemetryAnalyticsRepository;
import com.argusiq.tracing.repository.TelemetryAnalyticsRepository.OperationAggregate;
import com.argusiq.tracing.repository.TelemetryAnalyticsRepository.ServiceAggregate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ServicesBackendService {

    private static final int OPERATION_LIMIT = 10;
    private static final int RECENT_TRACE_LIMIT = 10;

    private final MonitoredServiceRepository serviceRepository;
    private final SpanRepository spanRepository;
    private final TelemetryAnalyticsRepository analyticsRepository;
    private final TraceService traceService;

    public ServicesBackendService(
            MonitoredServiceRepository serviceRepository,
            SpanRepository spanRepository,
            TelemetryAnalyticsRepository analyticsRepository,
            TraceService traceService
    ) {
        this.serviceRepository = serviceRepository;
        this.spanRepository = spanRepository;
        this.analyticsRepository = analyticsRepository;
        this.traceService = traceService;
    }

    @Transactional(readOnly = true)
    public List<ServiceResponse> getServices() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        Map<String, ServiceAggregate> aggregates = analyticsRepository.serviceAggregates(now).stream()
                .collect(Collectors.toMap(ServiceAggregate::serviceName, Function.identity()));
        Map<String, List<OperationMetricDto>> operationsByService = analyticsRepository.operationAggregates().stream()
                .map(this::operationMetric)
                .collect(Collectors.groupingBy(OperationMetricDto::serviceName));
        Map<String, Long> dependencyCounts = dependencyCounts(dependencyEdges());

        return serviceRepository.findAll().stream()
                .sorted(Comparator.comparing(MonitoredService::getServiceName))
                .map(service -> mapService(
                        service,
                        aggregates.getOrDefault(service.getServiceName(), ServiceAggregate.empty(service.getServiceName())),
                        operationsByService.getOrDefault(service.getServiceName(), List.of()),
                        dependencyCounts.getOrDefault(service.getServiceName(), 0L),
                        false,
                        now
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<ServiceResponse> getService(Long id) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        List<DependencyEdgeDto> edges = dependencyEdges();
        return serviceRepository.findById(id).map(service -> mapService(
                service,
                analyticsRepository.serviceAggregate(service.getServiceName(), now),
                analyticsRepository.operationAggregates(service.getServiceName()).stream()
                        .map(this::operationMetric)
                        .toList(),
                dependencyCounts(edges).getOrDefault(service.getServiceName(), 0L),
                true,
                now
        ));
    }

    @Transactional(readOnly = true)
    public DependencyGraphResponse getDependencyGraph() {
        List<DependencyEdgeDto> edges = dependencyEdges();
        Set<String> nodes = new LinkedHashSet<>(serviceRepository.findAllServiceNames());
        edges.forEach(edge -> {
            nodes.add(edge.getSource());
            nodes.add(edge.getTarget());
        });
        return new DependencyGraphResponse(List.copyOf(nodes), edges);
    }

    private ServiceResponse mapService(
            MonitoredService service,
            ServiceAggregate aggregate,
            List<OperationMetricDto> operations,
            long dependencyCount,
            boolean includeRecent,
            LocalDateTime now
    ) {
        List<OperationMetricDto> slowestFirst = operations.stream()
                .sorted(Comparator.comparing(OperationMetricDto::averageLatencyMs).reversed()
                        .thenComparing(OperationMetricDto::operationName))
                .toList();
        List<OperationMetricDto> fastestFirst = operations.stream()
                .sorted(Comparator.comparing(OperationMetricDto::averageLatencyMs)
                        .thenComparing(OperationMetricDto::operationName))
                .toList();
        List<OperationMetricDto> byTraffic = operations.stream()
                .sorted(Comparator.comparingLong(OperationMetricDto::observationCount).reversed()
                        .thenComparing(OperationMetricDto::operationName))
                .limit(OPERATION_LIMIT)
                .toList();
        Double errorRate = percentage(aggregate.errorCount(), aggregate.requestCount());
        Double successRate = errorRate != null ? 100.0 - errorRate : null;

        return new ServiceResponse(
                service.getId(),
                service.getServiceName(),
                service.getEnvironment(),
                service.getVersion(),
                service.getLanguage(),
                telemetryStatus(service.getLastSeen(), aggregate, now),
                aggregate.requestCount(),
                aggregate.errorCount(),
                errorRate,
                successRate,
                aggregate.requestsPerMinute(),
                aggregate.averageLatencyMs(),
                aggregate.p95LatencyMs(),
                aggregate.p99LatencyMs(),
                aggregate.minimumLatencyMs(),
                aggregate.maximumLatencyMs(),
                operations.size(),
                dependencyCount,
                slowestFirst.isEmpty() ? null : slowestFirst.getFirst(),
                fastestFirst.isEmpty() ? null : fastestFirst.getFirst(),
                byTraffic,
                includeRecent ? traceService.recentTracesForService(service.getServiceName(), RECENT_TRACE_LIMIT) : List.of(),
                includeRecent ? traceService.recentErrorsForService(service.getServiceName(), RECENT_TRACE_LIMIT) : List.of(),
                service.getFirstSeen(),
                service.getLastSeen(),
                observationAgeMinutes(service.getFirstSeen(), now)
        );
    }

    private String telemetryStatus(LocalDateTime lastSeen, ServiceAggregate aggregate, LocalDateTime now) {
        if (lastSeen == null || lastSeen.isBefore(now.minusMinutes(5))) {
            return "STALE";
        }
        Double recentErrorRate = percentage(
                aggregate.errorsLastFiveMinutes(),
                aggregate.requestsLastFiveMinutes()
        );
        if (recentErrorRate != null && recentErrorRate >= 10.0) {
            return "ERRORING";
        }
        return "ACTIVE";
    }

    private Long observationAgeMinutes(LocalDateTime firstSeen, LocalDateTime now) {
        return firstSeen != null ? Math.max(0L, Duration.between(firstSeen, now).toMinutes()) : null;
    }

    private Double percentage(long numerator, long denominator) {
        return denominator > 0 ? numerator * 100.0 / denominator : null;
    }

    private OperationMetricDto operationMetric(OperationAggregate aggregate) {
        return new OperationMetricDto(
                aggregate.serviceName(),
                aggregate.operationName(),
                aggregate.observationCount(),
                aggregate.averageLatencyMs(),
                aggregate.minimumLatencyMs(),
                aggregate.maximumLatencyMs(),
                aggregate.errorCount()
        );
    }

    private List<DependencyEdgeDto> dependencyEdges() {
        return spanRepository.findServiceDependencies().stream()
                .map(row -> new DependencyEdgeDto(String.valueOf(row[0]), String.valueOf(row[1])))
                .distinct()
                .toList();
    }

    private Map<String, Long> dependencyCounts(List<DependencyEdgeDto> edges) {
        return edges.stream().collect(Collectors.groupingBy(DependencyEdgeDto::getSource, Collectors.counting()));
    }
}
