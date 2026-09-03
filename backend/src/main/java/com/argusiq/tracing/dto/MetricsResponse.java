package com.argusiq.tracing.dto;

import java.util.List;
import java.util.Map;

public record MetricsResponse(
        long totalTraces,
        Double averageLatencyMs,
        Double medianLatencyMs,
        Double p50LatencyMs,
        Double p90LatencyMs,
        Double p95LatencyMs,
        Double p99LatencyMs,
        Long minimumLatencyMs,
        Long maximumLatencyMs,
        long errorCount,
        Double errorRate,
        Double successRate,
        long requestsPerMinute,
        long requestsPerHour,
        long requestsPerDay,
        long uniqueEndpoints,
        long uniqueServices,
        List<EndpointMetricDto> slowestEndpoints,
        List<EndpointMetricDto> fastestEndpoints,
        List<EndpointMetricDto> topEndpointsByTraffic,
        List<EndpointMetricDto> mostFailingEndpoints,
        List<OperationMetricDto> slowestOperations,
        Map<String, Long> traceDurationHistogram,
        Map<String, Long> statusCodeDistribution,
        Map<String, Long> httpMethodDistribution
) {
    public MetricsResponse {
        slowestEndpoints = slowestEndpoints != null ? List.copyOf(slowestEndpoints) : List.of();
        fastestEndpoints = fastestEndpoints != null ? List.copyOf(fastestEndpoints) : List.of();
        topEndpointsByTraffic = topEndpointsByTraffic != null ? List.copyOf(topEndpointsByTraffic) : List.of();
        mostFailingEndpoints = mostFailingEndpoints != null ? List.copyOf(mostFailingEndpoints) : List.of();
        slowestOperations = slowestOperations != null ? List.copyOf(slowestOperations) : List.of();
        traceDurationHistogram = traceDurationHistogram != null ? Map.copyOf(traceDurationHistogram) : Map.of();
        statusCodeDistribution = statusCodeDistribution != null ? Map.copyOf(statusCodeDistribution) : Map.of();
        httpMethodDistribution = httpMethodDistribution != null ? Map.copyOf(httpMethodDistribution) : Map.of();
    }
}
