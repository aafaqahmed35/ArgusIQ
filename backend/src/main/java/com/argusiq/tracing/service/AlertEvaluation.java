package com.argusiq.tracing.service;

import com.argusiq.tracing.dto.AlertEvidence;

import java.time.LocalDateTime;

public record AlertEvaluation(
        LocalDateTime evaluationTime,
        LocalDateTime windowStart,
        LocalDateTime windowEnd,
        String summary,
        String traceId,
        String spanId,
        String serviceName,
        AlertEvidence evidence
) {
}
