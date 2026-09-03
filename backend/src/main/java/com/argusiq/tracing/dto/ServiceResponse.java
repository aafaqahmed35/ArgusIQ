package com.argusiq.tracing.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ServiceResponse(
        Long id,
        String serviceName,
        String environment,
        String version,
        String language,
        String telemetryStatus,
        long requestCount,
        long errorCount,
        Double errorRate,
        Double successRate,
        long requestsPerMinute,
        Double averageLatencyMs,
        Double p95LatencyMs,
        Double p99LatencyMs,
        Long minimumLatencyMs,
        Long maximumLatencyMs,
        long observedOperationCount,
        long dependencyCount,
        OperationMetricDto slowestOperation,
        OperationMetricDto fastestOperation,
        List<OperationMetricDto> topOperationsByTraffic,
        List<TraceResponseDto> recentTraces,
        List<TraceResponseDto> recentErrors,
        LocalDateTime firstSeen,
        LocalDateTime lastSeen,
        Long observationAgeMinutes
) {
    public ServiceResponse {
        topOperationsByTraffic = topOperationsByTraffic != null ? List.copyOf(topOperationsByTraffic) : List.of();
        recentTraces = recentTraces != null ? List.copyOf(recentTraces) : List.of();
        recentErrors = recentErrors != null ? List.copyOf(recentErrors) : List.of();
    }
}
