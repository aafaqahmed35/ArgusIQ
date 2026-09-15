package com.argusiq.tracing.dto;

import java.time.LocalDateTime;

public record AlertEvidence(
        String metric,
        Double observedValue,
        Double threshold,
        String unit,
        Long sampleCount,
        Long errorCount,
        LocalDateTime windowStart,
        LocalDateTime windowEnd,
        LocalDateTime observedAt,
        String traceId,
        String spanId,
        String serviceName,
        String operationName,
        String status,
        Integer httpStatus
) {
}
