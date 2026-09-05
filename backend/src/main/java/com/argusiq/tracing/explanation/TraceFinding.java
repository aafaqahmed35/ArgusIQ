package com.argusiq.tracing.explanation;

import java.util.List;

/**
 * One ranked, evidence-backed observation that may be worth investigating.
 */
public record TraceFinding(
        String code,
        Category category,
        Significance significance,
        String title,
        String description,
        List<TraceFindingEvidence> evidence,
        EvidenceStrength evidenceStrength
) {
    public enum Category {
        ERROR,
        STRUCTURAL_LATENCY,
        RELATIONSHIP,
        CONCURRENCY,
        LIMITATION
    }

    public enum Significance {
        HIGH,
        MEDIUM,
        INFORMATIONAL
    }

    public enum EvidenceStrength {
        HIGH,
        MEDIUM,
        LOW
    }

    public TraceFinding {
        evidence = evidence != null ? List.copyOf(evidence) : List.of();
    }
}
