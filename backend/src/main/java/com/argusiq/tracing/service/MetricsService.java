package com.argusiq.tracing.service;

import com.argusiq.tracing.dto.MetricsResponse;
import com.argusiq.tracing.dto.NamedMetricDto;
import com.argusiq.tracing.repository.SpanRepository;
import com.argusiq.tracing.repository.TraceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class MetricsService {

    private static final long CACHE_TTL_MS = 30_000L;

    private final TraceRepository traceRepository;
    private final SpanRepository spanRepository;
    private final AtomicReference<CachedMetrics> cache = new AtomicReference<>();

    public MetricsService(TraceRepository traceRepository, SpanRepository spanRepository) {
        this.traceRepository = traceRepository;
        this.spanRepository = spanRepository;
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

    public void invalidate() {
        cache.set(null);
    }

    @Transactional(readOnly = true)
    public MetricsResponse computeMetrics() {
        long total = traceRepository.count();
        long errors = traceRepository.countErrors();
        List<Long> durations = traceRepository.findAllDurationsSorted();

        double avg = nullableDouble(traceRepository.findAverageDurationMs());
        long min = traceRepository.findMinDurationMs() != null ? traceRepository.findMinDurationMs() : 0L;
        long max = traceRepository.findMaxDurationMs() != null ? traceRepository.findMaxDurationMs() : 0L;
        double errorRate = total > 0 ? (errors * 100.0) / total : 0.0;
        double successRate = total > 0 ? 100.0 - errorRate : 0.0;

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        long perMinute = traceRepository.countSince(now.minusMinutes(1));
        long perHour = traceRepository.countSince(now.minusHours(1));
        long perDay = traceRepository.countSince(now.minusDays(1));

        return new MetricsResponse(
                avg,
                percentile(durations, 50),
                percentile(durations, 50),
                percentile(durations, 90),
                percentile(durations, 95),
                percentile(durations, 99),
                min,
                max,
                errorRate,
                successRate,
                total,
                perMinute,
                perHour,
                perDay,
                traceRepository.countUniqueEndpoints(),
                traceRepository.countUniqueServices(),
                metricRows(traceRepository.findEndpointLatencyRankingDesc(), 10),
                metricRows(traceRepository.findEndpointLatencyRankingAsc(), 10),
                countRows(traceRepository.findMostFailingEndpoints(), 10),
                countRows(spanRepository.findMostActiveServices(), 10),
                operationRows(spanRepository.findMostExpensiveOperations(), 10),
                histogram(durations),
                distribution(traceRepository.findStatusCodeDistribution()),
                distribution(traceRepository.findHttpMethodDistribution())
        );
    }

    public List<NamedMetricDto> endpointMetrics() {
        return metricRows(traceRepository.findEndpointLatencyRankingDesc(), 100);
    }

    public List<NamedMetricDto> serviceMetrics() {
        return traceRepository.findServiceMetricRows().stream()
                .map(row -> new NamedMetricDto(String.valueOf(row[0]), nullableDouble(row[1]), ((Number) row[2]).longValue()))
                .toList();
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

    private List<NamedMetricDto> metricRows(List<Object[]> rows, int limit) {
        return rows.stream()
                .limit(limit)
                .map(row -> new NamedMetricDto(String.valueOf(row[0]), nullableDouble(row[1]), ((Number) row[2]).longValue()))
                .toList();
    }

    private List<NamedMetricDto> countRows(List<Object[]> rows, int limit) {
        return rows.stream()
                .limit(limit)
                .map(row -> new NamedMetricDto(String.valueOf(row[0]), ((Number) row[1]).doubleValue(), ((Number) row[1]).longValue()))
                .toList();
    }

    private List<NamedMetricDto> operationRows(List<Object[]> rows, int limit) {
        return rows.stream()
                .limit(limit)
                .map(row -> new NamedMetricDto(row[1] + ":" + row[0], nullableDouble(row[2]), ((Number) row[4]).longValue()))
                .toList();
    }

    private Map<String, Long> distribution(List<Object[]> rows) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (Object[] row : rows) {
            result.put(String.valueOf(row[0] != null ? row[0] : "UNKNOWN"), ((Number) row[1]).longValue());
        }
        return result;
    }

    private Map<String, Long> histogram(List<Long> durations) {
        Map<String, Long> buckets = new LinkedHashMap<>();
        buckets.put("0-100ms", 0L);
        buckets.put("101-250ms", 0L);
        buckets.put("251-500ms", 0L);
        buckets.put("501-1000ms", 0L);
        buckets.put("1001-2500ms", 0L);
        buckets.put("2501ms+", 0L);
        for (Long duration : durations) {
            String bucket = duration <= 100 ? "0-100ms"
                    : duration <= 250 ? "101-250ms"
                    : duration <= 500 ? "251-500ms"
                    : duration <= 1000 ? "501-1000ms"
                    : duration <= 2500 ? "1001-2500ms"
                    : "2501ms+";
            buckets.compute(bucket, (key, value) -> value == null ? 1L : value + 1);
        }
        return buckets;
    }

    private double nullableDouble(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }

    private record CachedMetrics(MetricsResponse metrics, long createdAtMs) {
    }
}
