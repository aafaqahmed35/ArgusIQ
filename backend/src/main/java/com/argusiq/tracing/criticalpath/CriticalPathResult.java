package com.argusiq.tracing.criticalpath;

import java.util.List;

/**
 * Deterministic critical-path result for one trace snapshot.
 *
 * <p>The trace wall-clock duration is the envelope from the earliest valid
 * span start to the latest valid span end. The sum of span durations counts
 * every span independently and therefore double-counts nesting and overlap.
 * The longest span duration is only a span-level maximum. The critical-path
 * duration instead sums span self-time plus an optimal non-overlapping set of
 * causally linked child paths. Contributions therefore never double-count a
 * wall-clock interval.</p>
 */
public record CriticalPathResult(
        String algorithm,
        Status status,
        String rootSpanId,
        long totalDurationMs,
        long traceWallClockDurationMs,
        long sumSpanDurationsMs,
        long longestSpanDurationMs,
        List<CriticalPathSpanContribution> spans,
        List<String> issues
) {
    public static final String ALGORITHM = "INTERVAL_AWARE_CAUSAL_V1";

    public enum Status {
        COMPLETE,
        PARTIAL,
        UNAVAILABLE
    }

    public CriticalPathResult {
        spans = spans != null ? List.copyOf(spans) : List.of();
        issues = issues != null ? List.copyOf(issues) : List.of();
    }

    public boolean isComplete() {
        return status == Status.COMPLETE;
    }
}
