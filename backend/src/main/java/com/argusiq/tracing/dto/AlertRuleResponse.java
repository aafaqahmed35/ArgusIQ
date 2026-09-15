package com.argusiq.tracing.dto;

import java.time.LocalDateTime;

public record AlertRuleResponse(
        Long id,
        String name,
        String type,
        String severity,
        String serviceName,
        Double threshold,
        Long windowSeconds,
        Long minimumSamples,
        String comparator,
        boolean enabled,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
