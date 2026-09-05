package com.argusiq.tracing.explanation;

import java.util.List;

/**
 * Deterministic investigation guidance derived from one persisted trace snapshot.
 */
public record TraceExplanation(
        Status status,
        String summary,
        List<TraceFinding> findings,
        List<TraceExplanationLimitation> limitations
) {
    public enum Status {
        /** Available valid structural snapshot evaluated without graph-integrity limitations. */
        COMPLETE,
        /** A valid canonical component was evaluated, with explicit graph-integrity limitations. */
        PARTIAL,
        /** No valid canonical structural path was available for evaluation. */
        INSUFFICIENT_EVIDENCE
    }

    public TraceExplanation {
        findings = findings != null ? List.copyOf(findings) : List.of();
        limitations = limitations != null ? List.copyOf(limitations) : List.of();
    }
}
