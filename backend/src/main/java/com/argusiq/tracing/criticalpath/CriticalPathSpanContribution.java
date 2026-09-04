package com.argusiq.tracing.criticalpath;

import java.time.LocalDateTime;

/**
 * Evidence for one span selected by the critical-path calculation.
 *
 * <p>{@code selfTimeMs} is the span interval minus the union of all validated
 * direct-child intervals. {@code contributionDurationMs} is the self-time that
 * this span contributes to the selected causal path; descendant contributions
 * are represented by their own entries.</p>
 */
public record CriticalPathSpanContribution(
        String spanId,
        String parentSpanId,
        String serviceName,
        String operationName,
        String kind,
        LocalDateTime startTime,
        LocalDateTime endTime,
        long durationMs,
        long selfTimeMs,
        long contributionDurationMs
) {
}
