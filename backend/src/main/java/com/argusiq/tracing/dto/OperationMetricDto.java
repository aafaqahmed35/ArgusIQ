package com.argusiq.tracing.dto;

public record OperationMetricDto(
        String serviceName,
        String operationName,
        long observationCount,
        Double averageLatencyMs,
        Long minimumLatencyMs,
        Long maximumLatencyMs,
        long errorCount
) {
}
