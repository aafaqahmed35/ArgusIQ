package com.argusiq.tracing.service;

import com.argusiq.tracing.dto.EndpointMetricDto;
import com.argusiq.tracing.dto.MetricsResponse;
import com.argusiq.tracing.dto.OperationMetricDto;
import com.argusiq.tracing.event.TelemetryChangedEvent;
import com.argusiq.tracing.repository.TelemetryAnalyticsRepository;
import com.argusiq.tracing.repository.TelemetryAnalyticsRepository.EndpointAggregate;
import com.argusiq.tracing.repository.TelemetryAnalyticsRepository.GlobalTraceAggregate;
import com.argusiq.tracing.repository.TelemetryAnalyticsRepository.OperationAggregate;
import com.argusiq.tracing.repository.TraceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class MetricsService {

    static final long CACHE_TTL_MS = 30_000L;
    private static final int RANKING_LIMIT = 10;
    private static final int MAX_ENDPOINT_LIMIT = 100;

    private final TelemetryAnalyticsRepository analyticsRepository;
    private final TraceRepository traceRepository;
    private final AtomicReference<CachedMetrics> cache = new AtomicReference<>();

    public MetricsService(TelemetryAnalyticsRepository analyticsRepository, TraceRepository traceRepository) {
        this.analyticsRepository = analyticsRepository;
        this.traceRepository = traceRepository;
    }

    @Transactional(readOnly = true)
    public MetricsResponse getMetrics() {
        CachedMetrics cached = cache.get();
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.createdAtMs() < CACHE_TTL_MS) {
            return cached.metrics();
        }

        MetricsResponse metrics = computeMetrics();
        cache.set(new CachedMetrics(metrics, now));
        return metrics;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onTelemetryChanged(TelemetryChangedEvent ignored) {
        invalidate();
    }

    public void invalidate() {
        cache.set(null);
    }

    @Transactional(readOnly = true)
    public MetricsResponse computeMetrics() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        GlobalTraceAggregate aggregate = analyticsRepository.globalTraceAggregate(now);
        List<OperationMetricDto> operations = analyticsRepository.slowestOperationAggregates(RANKING_LIMIT).stream()
                .map(this::operationMetric)
                .toList();

        Double errorRate = percentage(aggregate.errorCount(), aggregate.totalTraces());
        Double successRate = errorRate != null ? 100.0 - errorRate : null;

        return new MetricsResponse(
                aggregate.totalTraces(),
                aggregate.averageLatencyMs(),
                aggregate.p50LatencyMs(),
                aggregate.p50LatencyMs(),
                aggregate.p90LatencyMs(),
                aggregate.p95LatencyMs(),
                aggregate.p99LatencyMs(),
                aggregate.minimumLatencyMs(),
                aggregate.maximumLatencyMs(),
                aggregate.errorCount(),
                errorRate,
                successRate,
                aggregate.requestsPerMinute(),
                aggregate.requestsPerHour(),
                aggregate.requestsPerDay(),
                aggregate.uniqueEndpoints(),
                aggregate.uniqueServices(),
                endpointMetrics("latency", "desc", RANKING_LIMIT),
                endpointMetrics("latency", "asc", RANKING_LIMIT),
                endpointMetrics("traffic", "desc", RANKING_LIMIT),
                endpointMetrics("errors", "desc", RANKING_LIMIT),
                operations,
                histogram(aggregate),
                distribution(traceRepository.findStatusCodeDistribution()),
                distribution(traceRepository.findHttpMethodDistribution())
        );
    }

    @Transactional(readOnly = true)
    public List<EndpointMetricDto> endpointMetrics(String sortBy, String sortDirection, int requestedLimit) {
        if (!List.of("traffic", "latency", "errors").contains(sortBy)) {
            throw new IllegalArgumentException("sortBy must be traffic, latency, or errors");
        }
        if (!"asc".equalsIgnoreCase(sortDirection) && !"desc".equalsIgnoreCase(sortDirection)) {
            throw new IllegalArgumentException("sortDirection must be asc or desc");
        }
        int limit = Math.max(1, Math.min(requestedLimit, MAX_ENDPOINT_LIMIT));
        return analyticsRepository.endpointAggregates(sortBy, sortDirection, limit).stream()
                .map(this::endpointMetric)
                .toList();
    }

    private EndpointMetricDto endpointMetric(EndpointAggregate aggregate) {
        return new EndpointMetricDto(
                aggregate.endpoint(),
                aggregate.requestCount(),
                aggregate.averageLatencyMs(),
                aggregate.p95LatencyMs(),
                aggregate.errorCount(),
                percentage(aggregate.errorCount(), aggregate.requestCount()),
                aggregate.minimumLatencyMs(),
                aggregate.maximumLatencyMs()
        );
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

    private Double percentage(long numerator, long denominator) {
        return denominator > 0 ? numerator * 100.0 / denominator : null;
    }

    private Map<String, Long> histogram(GlobalTraceAggregate aggregate) {
        Map<String, Long> buckets = new LinkedHashMap<>();
        buckets.put("0-100ms", aggregate.duration0To100());
        buckets.put("101-250ms", aggregate.duration101To250());
        buckets.put("251-500ms", aggregate.duration251To500());
        buckets.put("501-1000ms", aggregate.duration501To1000());
        buckets.put("1001-2500ms", aggregate.duration1001To2500());
        buckets.put("2501ms+", aggregate.duration2501Plus());
        return buckets;
    }

    private Map<String, Long> distribution(List<Object[]> rows) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (Object[] row : rows) {
            result.put(String.valueOf(row[0] != null ? row[0] : "UNKNOWN"), ((Number) row[1]).longValue());
        }
        return result;
    }

    private record CachedMetrics(MetricsResponse metrics, long createdAtMs) {
    }
}
