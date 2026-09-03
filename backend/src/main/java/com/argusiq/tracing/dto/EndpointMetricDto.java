package com.argusiq.tracing.dto;

public record EndpointMetricDto(
        String endpoint,
        long requestCount,
        Double averageLatencyMs,
        Double p95LatencyMs,
        long errorCount,
        Double errorRate,
        Long minimumLatencyMs,
        Long maximumLatencyMs
) {
}
