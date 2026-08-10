package com.argusiq.tracing.service;

import com.argusiq.tracing.dto.DependencyEdgeDto;
import com.argusiq.tracing.dto.DependencyGraphResponse;
import com.argusiq.tracing.dto.NamedMetricDto;
import com.argusiq.tracing.dto.ServiceResponse;
import com.argusiq.tracing.entity.MonitoredService;
import com.argusiq.tracing.repository.MonitoredServiceRepository;
import com.argusiq.tracing.repository.SpanRepository;
import com.argusiq.tracing.repository.TraceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class ServicesBackendService {

    private final MonitoredServiceRepository serviceRepository;
    private final TraceRepository traceRepository;
    private final SpanRepository spanRepository;
    private final TraceService traceService;

    public ServicesBackendService(MonitoredServiceRepository serviceRepository, TraceRepository traceRepository, SpanRepository spanRepository, TraceService traceService) {
        this.serviceRepository = serviceRepository;
        this.traceRepository = traceRepository;
        this.spanRepository = spanRepository;
        this.traceService = traceService;
    }

    @Transactional(readOnly = true)
    public List<ServiceResponse> getServices() {
        return serviceRepository.findAll().stream()
                .map(service -> mapService(service, false))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<ServiceResponse> getService(Long id) {
        return serviceRepository.findById(id).map(service -> mapService(service, true));
    }

    @Transactional(readOnly = true)
    public DependencyGraphResponse getDependencyGraph() {
        List<DependencyEdgeDto> edges = spanRepository.findServiceDependencies().stream()
                .map(row -> new DependencyEdgeDto(String.valueOf(row[0]), String.valueOf(row[1])))
                .distinct()
                .toList();
        Set<String> nodes = new LinkedHashSet<>(serviceRepository.findAllServiceNames());
        edges.forEach(edge -> {
            nodes.add(edge.getSource());
            nodes.add(edge.getTarget());
        });
        return new DependencyGraphResponse(List.copyOf(nodes), edges);
    }

    private ServiceResponse mapService(MonitoredService service, boolean includeRecent) {
        String serviceName = service.getServiceName();
        long requestCount = traceRepository.countByServiceName(serviceName);
        long failures = traceRepository.countErrorsByServiceName(serviceName);
        List<Long> durations = traceRepository.findDurationsSortedByServiceName(serviceName);
        double average = nullableDouble(traceRepository.findAverageDurationMsByServiceName(serviceName));
        double errorRate = requestCount > 0 ? failures * 100.0 / requestCount : 0.0;
        double availability = requestCount > 0 ? 100.0 - errorRate : 100.0;
        long throughput = spanRepository.countByServiceSince(serviceName, LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        long uptime = service.getFirstSeen() != null ? Duration.between(service.getFirstSeen(), LocalDateTime.now(ZoneOffset.UTC)).toMinutes() : 0L;
        List<NamedMetricDto> topEndpoints = traceRepository.findTopEndpointsByServiceName(serviceName).stream()
                .limit(10)
                .map(row -> new NamedMetricDto(String.valueOf(row[0]), ((Number) row[1]).doubleValue(), ((Number) row[1]).longValue()))
                .toList();
        List<NamedMetricDto> errorOps = spanRepository.findMostCommonErrors(serviceName).stream()
                .limit(10)
                .map(row -> new NamedMetricDto(String.valueOf(row[0]), ((Number) row[1]).doubleValue(), ((Number) row[1]).longValue()))
                .toList();
        List<Object[]> ops = spanRepository.findTopOperations(serviceName);
        String slowest = ops.stream().map(row -> String.valueOf(row[0])).findFirst().orElse(null);
        String fastest = ops.stream().sorted(Comparator.comparing(row -> String.valueOf(row[0]))).map(row -> String.valueOf(row[0])).findFirst().orElse(null);
        long dependencies = getDependencyGraph().getEdges().stream()
                .filter(edge -> serviceName.equals(edge.getSource()))
                .count();

        return new ServiceResponse(
                service.getId(),
                serviceName,
                health(errorRate, service.getLastSeen()),
                average,
                percentile(durations, 95),
                percentile(durations, 99),
                errorRate,
                throughput,
                topEndpoints.size(),
                dependencies,
                includeRecent ? traceService.recentTracesForService(serviceName, 10) : List.of(),
                includeRecent ? traceService.recentErrorsForService(serviceName, 10) : List.of(),
                availability,
                Math.max(0L, uptime),
                requestCount,
                traceRepository.findMaxDurationMsByServiceName(serviceName) != null ? traceRepository.findMaxDurationMsByServiceName(serviceName) : 0L,
                failures,
                slowest,
                fastest,
                topEndpoints,
                errorOps,
                service.getFirstSeen(),
                service.getLastSeen()
        );
    }

    private String health(double errorRate, LocalDateTime lastSeen) {
        if (lastSeen == null || lastSeen.isBefore(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5))) {
            return "UNAVAILABLE";
        }
        if (errorRate >= 10.0) {
            return "DEGRADED";
        }
        return "HEALTHY";
    }

    private double percentile(List<Long> sortedDurations, int percentile) {
        if (sortedDurations == null || sortedDurations.isEmpty()) {
            return 0.0;
        }
        double rank = (percentile / 100.0) * (sortedDurations.size() - 1);
        int lower = (int) Math.floor(rank);
        int upper = (int) Math.ceil(rank);
        if (lower == upper) {
            return sortedDurations.get(lower);
        }
        double weight = rank - lower;
        return sortedDurations.get(lower) * (1.0 - weight) + sortedDurations.get(upper) * weight;
    }

    private double nullableDouble(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }
}
