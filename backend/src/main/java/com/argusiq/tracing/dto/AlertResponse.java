package com.argusiq.tracing.dto;

import java.time.LocalDateTime;

public record AlertResponse(
        Long alertId,
        Long ruleId,
        String ruleName,
        String severity,
        String status,
        String type,
        String title,
        String description,
        LocalDateTime firstTriggeredAt,
        LocalDateTime lastTriggeredAt,
        LocalDateTime evaluationTime,
        LocalDateTime resolvedAt,
        String relatedTrace,
        String relatedSpan,
        String relatedService,
        AlertEvidence evidence,
        boolean acknowledged,
        LocalDateTime acknowledgedAt,
        String lastEvaluationState
) {
}
