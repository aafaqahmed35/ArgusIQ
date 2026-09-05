package com.argusiq.tracing.explanation;

import com.argusiq.tracing.criticalpath.CriticalPathResult;
import com.argusiq.tracing.criticalpath.CriticalPathSpanContribution;
import com.argusiq.tracing.criticalpath.TraceStructuralEvidence;
import com.argusiq.tracing.entity.SpanEntity;
import com.argusiq.tracing.explanation.TraceFinding.Category;
import com.argusiq.tracing.explanation.TraceFinding.EvidenceStrength;
import com.argusiq.tracing.explanation.TraceFinding.Significance;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Produces deterministic investigation findings from one in-memory span
 * snapshot and its structural critical-path result.
 *
 * <p>The engine reports observed status/timing evidence and conservative
 * structural relationships. Parent identifiers and timestamps do not prove
 * synchronous waiting or an exact causal mechanism.</p>
 */
@Component
public class TraceExplanationEngine {

    static final int CONCENTRATION_PERCENT = 50;
    static final int CROSS_SERVICE_PERCENT = 20;
    static final int DOMINANT_SELF_TIME_PERCENT = 40;
    static final int OVERLAP_PERCENT = 20;
    static final long MIN_CONTRIBUTION_MS = 100;
    static final long MIN_SELF_TIME_MS = 250;
    static final long MIN_OVERLAP_MS = 100;

    private static final String STRUCTURAL_MODEL_LIMITATION =
            "Parent relationships and timestamps provide structural evidence; they do not prove synchronous blocking.";

    private static final Comparator<SpanEntity> SPAN_ORDER = Comparator
            .comparing(TraceExplanationEngine::spanId)
            .thenComparing(span -> span.getStartTime(), Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(span -> span.getEndTime(), Comparator.nullsLast(Comparator.naturalOrder()));

    private static final Map<String, String> ISSUE_DESCRIPTIONS = Map.ofEntries(
            Map.entry("NO_SPANS", "No spans are available for structural analysis."),
            Map.entry("MISSING_SPAN_ID", "At least one span is missing an identity."),
            Map.entry("MALFORMED_SPAN_TIMESTAMPS", "At least one span has malformed timestamps."),
            Map.entry("DUPLICATE_SPAN_ID", "Duplicate span identities make the structural graph ambiguous."),
            Map.entry("TRACE_ID_MISMATCH", "The loaded spans do not all belong to the same trace identity."),
            Map.entry("CYCLIC_PARENT_GRAPH", "A cycle exists in the reported parent relationships."),
            Map.entry("PREFERRED_ROOT_UNAVAILABLE", "The selected root span is unavailable for structural analysis."),
            Map.entry("MISSING_PARENT", "At least one span references a parent that is not present."),
            Map.entry("CHILD_OUTSIDE_PARENT_BOUNDS", "At least one child interval falls outside its reported parent interval."),
            Map.entry("MULTIPLE_ROOT_CANDIDATES", "Multiple root candidates are present in the trace."),
            Map.entry("DISCONNECTED_SPANS", "Some spans are disconnected from the selected root component.")
    );

    public TraceExplanation explain(Collection<SpanEntity> spanSnapshot, CriticalPathResult criticalPath) {
        List<SpanEntity> spans = spanSnapshot == null
                ? List.of()
                : spanSnapshot.stream().filter(Objects::nonNull).sorted(SPAN_ORDER).toList();
        Map<String, SpanEntity> spansById = new LinkedHashMap<>();
        for (SpanEntity span : spans) {
            spansById.putIfAbsent(spanId(span), span);
        }

        TraceExplanation.Status status = explanationStatus(criticalPath);
        List<TraceExplanationLimitation> limitations = limitations(criticalPath);
        List<TraceFinding> findings = new ArrayList<>();
        Set<String> criticalSpanIds = criticalSpanIds(criticalPath);
        Map<String, String> validatedParentSpanIds = TraceStructuralEvidence.validatedParentSpanIds(spans);

        if (hasStructuralPath(criticalPath)) {
            addCriticalPathErrors(findings, criticalPath, spansById);
            addConcentration(findings, criticalPath, spansById);
            addCrossServiceTransition(findings, criticalPath, spansById, validatedParentSpanIds);
            addDominantSelfTime(findings, criticalPath, spansById);
            addRelevantOverlap(findings, spans, spansById, criticalPath, validatedParentSpanIds);
        }
        addOffPathErrors(findings, spans, spansById, criticalSpanIds, criticalPath, validatedParentSpanIds);
        addIncompleteEvidenceFinding(findings, criticalPath, limitations);

        findings.sort(Comparator
                .comparingInt((TraceFinding finding) -> findingPriority(finding.code()))
                .thenComparing(TraceFinding::code)
                .thenComparing(TraceExplanationEngine::primarySpanId));

        return new TraceExplanation(
                status,
                summary(status, criticalPath, findings, limitations),
                findings,
                limitations
        );
    }

    private static void addCriticalPathErrors(
            List<TraceFinding> findings,
            CriticalPathResult criticalPath,
            Map<String, SpanEntity> spansById
    ) {
        for (CriticalPathSpanContribution contribution : criticalPath.spans()) {
            SpanEntity span = spansById.get(contribution.spanId());
            if (!isError(span)) {
                continue;
            }
            int percentage = percentage(contribution.contributionDurationMs(), criticalPath.totalDurationMs());
            findings.add(new TraceFinding(
                    "ERROR_ON_CRITICAL_PATH",
                    Category.ERROR,
                    Significance.HIGH,
                    "Error observed on structural critical path",
                    "An error was observed on " + label(span) + ", which contributes "
                            + contribution.contributionDurationMs() + " ms to the structural critical-path duration.",
                    List.of(evidence(span, contribution, null, percentage, null, "SELECTED_STRUCTURAL_PATH")),
                    directStrength(criticalPath)
            ));
        }
    }

    private static void addOffPathErrors(
            List<TraceFinding> findings,
            List<SpanEntity> spans,
            Map<String, SpanEntity> spansById,
            Set<String> criticalSpanIds,
            CriticalPathResult criticalPath,
            Map<String, String> validatedParentSpanIds
    ) {
        for (SpanEntity span : spans) {
            if (!isError(span) || criticalSpanIds.contains(spanId(span))) {
                continue;
            }
            String validatedParentSpanId = validatedParentSpanIds.get(spanId(span));
            SpanEntity parent = spansById.get(validatedParentSpanId);
            if (parent == null) {
                findings.add(new TraceFinding(
                        "ERROR_OUTSIDE_STRUCTURAL_PATH",
                        Category.ERROR,
                        Significance.HIGH,
                        "Error observed outside the selected structural path",
                        label(span) + " directly reported an error.",
                        List.of(evidence(span, null, null, null, null, "DIRECT_OBSERVATION")),
                        EvidenceStrength.HIGH
                ));
                continue;
            }
            findings.add(new TraceFinding(
                    "DOWNSTREAM_ERROR",
                    Category.ERROR,
                    Significance.HIGH,
                    "Downstream error observed",
                    label(span) + " reported an error beneath " + label(parent) + ".",
                    List.of(evidence(span, null, parent, null, null, "VALIDATED_CHILD_OF")),
                    derivedStrength(criticalPath)
            ));
        }
    }

    private static void addConcentration(
            List<TraceFinding> findings,
            CriticalPathResult criticalPath,
            Map<String, SpanEntity> spansById
    ) {
        if (criticalPath.spans().size() < 2 || criticalPath.totalDurationMs() <= 0) {
            return;
        }
        CriticalPathSpanContribution dominant = criticalPath.spans().stream()
                .max(Comparator.comparingLong(CriticalPathSpanContribution::contributionDurationMs)
                        .thenComparing(CriticalPathSpanContribution::spanId, Comparator.reverseOrder()))
                .orElse(null);
        if (dominant == null) {
            return;
        }
        int share = percentage(dominant.contributionDurationMs(), criticalPath.totalDurationMs());
        if (dominant.contributionDurationMs() < MIN_CONTRIBUTION_MS || share < CONCENTRATION_PERCENT) {
            return;
        }
        SpanEntity span = spansById.get(dominant.spanId());
        String evidenceLabel = span != null ? label(span) : label(dominant.serviceName(), dominant.operationName());
        findings.add(new TraceFinding(
                "CRITICAL_PATH_CONCENTRATION",
                Category.STRUCTURAL_LATENCY,
                share >= 75 ? Significance.HIGH : Significance.MEDIUM,
                "Structural latency is concentrated in one span",
                evidenceLabel + " contributes " + share + "% (" + dominant.contributionDurationMs()
                        + " ms) of the structural critical-path duration.",
                List.of(evidence(span, dominant, null, share, null, "SELECTED_STRUCTURAL_PATH")),
                directStrength(criticalPath)
        ));
    }

    private static void addCrossServiceTransition(
            List<TraceFinding> findings,
            CriticalPathResult criticalPath,
            Map<String, SpanEntity> spansById,
            Map<String, String> validatedParentSpanIds
    ) {
        if (criticalPath.totalDurationMs() <= 0) {
            return;
        }
        Map<String, CriticalPathSpanContribution> selectedById = new HashMap<>();
        criticalPath.spans().forEach(span -> selectedById.put(span.spanId(), span));
        CriticalPathSpanContribution strongest = criticalPath.spans().stream()
                .filter(child -> child.parentSpanId() != null)
                .filter(child -> selectedById.containsKey(child.parentSpanId()))
                .filter(child -> child.parentSpanId().equals(validatedParentSpanIds.get(child.spanId())))
                .filter(child -> differentServices(selectedById.get(child.parentSpanId()).serviceName(), child.serviceName()))
                .filter(child -> child.contributionDurationMs() >= MIN_CONTRIBUTION_MS)
                .filter(child -> percentage(child.contributionDurationMs(), criticalPath.totalDurationMs()) >= CROSS_SERVICE_PERCENT)
                .max(Comparator.comparingLong(CriticalPathSpanContribution::contributionDurationMs)
                        .thenComparing(CriticalPathSpanContribution::spanId, Comparator.reverseOrder()))
                .orElse(null);
        if (strongest == null) {
            return;
        }

        CriticalPathSpanContribution parentContribution = selectedById.get(strongest.parentSpanId());
        SpanEntity child = spansById.get(strongest.spanId());
        SpanEntity parent = spansById.get(strongest.parentSpanId());
        int share = percentage(strongest.contributionDurationMs(), criticalPath.totalDurationMs());
        findings.add(new TraceFinding(
                "CROSS_SERVICE_CRITICAL_PATH",
                Category.RELATIONSHIP,
                Significance.MEDIUM,
                "Structural path crosses a service boundary",
                label(parentContribution.serviceName(), parentContribution.operationName()) + " → "
                        + label(strongest.serviceName(), strongest.operationName()) + " is a validated parent/child transition; "
                        + strongest.contributionDurationMs() + " ms is attributed to the child operation.",
                List.of(evidence(child, strongest, parent, share, null, "VALIDATED_CHILD_OF")),
                derivedStrength(criticalPath)
        ));
    }

    private static void addDominantSelfTime(
            List<TraceFinding> findings,
            CriticalPathResult criticalPath,
            Map<String, SpanEntity> spansById
    ) {
        if (criticalPath.spans().size() < 2 || criticalPath.totalDurationMs() <= 0) {
            return;
        }
        CriticalPathSpanContribution dominant = criticalPath.spans().stream()
                .filter(span -> span.selfTimeMs() >= MIN_SELF_TIME_MS)
                .filter(span -> percentage(span.selfTimeMs(), criticalPath.totalDurationMs()) >= DOMINANT_SELF_TIME_PERCENT)
                .max(Comparator.comparingLong(CriticalPathSpanContribution::selfTimeMs)
                        .thenComparing(CriticalPathSpanContribution::spanId, Comparator.reverseOrder()))
                .orElse(null);
        if (dominant == null) {
            return;
        }
        SpanEntity span = spansById.get(dominant.spanId());
        int share = percentage(dominant.selfTimeMs(), criticalPath.totalDurationMs());
        findings.add(new TraceFinding(
                "LARGE_EXCLUSIVE_TIME",
                Category.STRUCTURAL_LATENCY,
                Significance.MEDIUM,
                "Large exclusive time observed",
                label(dominant.serviceName(), dominant.operationName()) + " contains " + dominant.selfTimeMs()
                        + " ms of exclusive time not covered by validated direct-child intervals.",
                List.of(evidence(span, dominant, null, share, null, "EXCLUSIVE_INTERVAL")),
                derivedStrength(criticalPath)
        ));
    }

    private static void addRelevantOverlap(
            List<TraceFinding> findings,
            List<SpanEntity> spans,
            Map<String, SpanEntity> spansById,
            CriticalPathResult criticalPath,
            Map<String, String> validatedParentSpanIds
    ) {
        long wallClockMs = criticalPath.traceWallClockDurationMs();
        if (wallClockMs <= 0) {
            return;
        }
        Map<String, List<SpanEntity>> childrenByParent = new HashMap<>();
        for (SpanEntity child : spans) {
            SpanEntity parent = spansById.get(validatedParentSpanIds.get(spanId(child)));
            if (parent != null) {
                childrenByParent.computeIfAbsent(parent.getSpanId(), ignored -> new ArrayList<>()).add(child);
            }
        }

        OverlapCandidate best = null;
        for (Map.Entry<String, List<SpanEntity>> entry : childrenByParent.entrySet()) {
            List<SpanEntity> children = entry.getValue().stream()
                    .sorted(Comparator.comparing(SpanEntity::getStartTime)
                            .thenComparing(SpanEntity::getEndTime)
                            .thenComparing(TraceExplanationEngine::spanId))
                    .toList();
            SpanEntity active = null;
            for (SpanEntity child : children) {
                if (active != null && active.getEndTime().isAfter(child.getStartTime())) {
                    LocalDateTime overlapEnd = active.getEndTime().isBefore(child.getEndTime())
                            ? active.getEndTime() : child.getEndTime();
                    long overlapMs = millisBetween(child.getStartTime(), overlapEnd);
                    OverlapCandidate candidate = new OverlapCandidate(
                            spansById.get(entry.getKey()), active, child, overlapMs
                    );
                    if (best == null || OVERLAP_ORDER.compare(candidate, best) < 0) {
                        best = candidate;
                    }
                }
                if (active == null || child.getEndTime().isAfter(active.getEndTime())) {
                    active = child;
                }
            }
        }
        if (best == null || best.overlapMs() < MIN_OVERLAP_MS
                || percentage(best.overlapMs(), wallClockMs) < OVERLAP_PERCENT) {
            return;
        }
        findings.add(new TraceFinding(
                "OVERLAPPING_CHILD_WORK",
                Category.CONCURRENCY,
                Significance.INFORMATIONAL,
                "Overlapping child work is not additive",
                label(best.first()) + " and " + label(best.second()) + " overlap for " + best.overlapMs()
                        + " ms beneath " + label(best.parent()) + "; their full durations should not be summed.",
                List.of(evidence(best.first(), null, best.second(), null, best.overlapMs(), "OVERLAPS_WITH")),
                derivedStrength(criticalPath)
        ));
    }

    private static final Comparator<OverlapCandidate> OVERLAP_ORDER = Comparator
            .comparingLong(OverlapCandidate::overlapMs).reversed()
            .thenComparing(candidate -> spanId(candidate.parent()))
            .thenComparing(candidate -> spanId(candidate.first()))
            .thenComparing(candidate -> spanId(candidate.second()));

    private static void addIncompleteEvidenceFinding(
            List<TraceFinding> findings,
            CriticalPathResult criticalPath,
            List<TraceExplanationLimitation> limitations
    ) {
        if (criticalPath != null && criticalPath.status() == CriticalPathResult.Status.COMPLETE) {
            return;
        }
        String state = criticalPath == null || criticalPath.status() == CriticalPathResult.Status.UNAVAILABLE
                ? "unavailable" : "partial";
        String details = limitations.stream()
                .filter(limitation -> !"STRUCTURAL_MODEL_ONLY".equals(limitation.code()))
                .map(TraceExplanationLimitation::description)
                .reduce((left, right) -> left + " " + right)
                .orElse("The structural trace graph is incomplete.");
        findings.add(new TraceFinding(
                "INCOMPLETE_STRUCTURAL_EVIDENCE",
                Category.LIMITATION,
                Significance.INFORMATIONAL,
                "Structural evidence is " + state,
                details,
                List.of(),
                EvidenceStrength.LOW
        ));
    }

    private static List<TraceExplanationLimitation> limitations(CriticalPathResult criticalPath) {
        List<TraceExplanationLimitation> limitations = new ArrayList<>();
        limitations.add(new TraceExplanationLimitation("STRUCTURAL_MODEL_ONLY", STRUCTURAL_MODEL_LIMITATION));
        if (criticalPath == null) {
            limitations.add(new TraceExplanationLimitation(
                    "CRITICAL_PATH_NOT_AVAILABLE",
                    "Structural critical-path evidence is not available for this trace."
            ));
            return List.copyOf(limitations);
        }
        for (String issue : criticalPath.issues()) {
            limitations.add(new TraceExplanationLimitation(
                    issue,
                    ISSUE_DESCRIPTIONS.getOrDefault(issue, "Structural analysis reported: " + readableCode(issue) + ".")
            ));
        }
        return List.copyOf(limitations);
    }

    private static String summary(
            TraceExplanation.Status status,
            CriticalPathResult criticalPath,
            List<TraceFinding> findings,
            List<TraceExplanationLimitation> limitations
    ) {
        long durationMs = criticalPath != null ? criticalPath.traceWallClockDurationMs() : 0;
        String prefix = "Trace completed in " + durationMs + " ms. ";
        if (status == TraceExplanation.Status.INSUFFICIENT_EVIDENCE) {
            String reason = limitations.stream()
                    .filter(limitation -> !"STRUCTURAL_MODEL_ONLY".equals(limitation.code()))
                    .map(TraceExplanationLimitation::description)
                    .findFirst()
                    .orElse("Structural critical-path evidence is unavailable.");
            return prefix + reason;
        }

        TraceFinding primary = findings.stream()
                .filter(finding -> !"INCOMPLETE_STRUCTURAL_EVIDENCE".equals(finding.code()))
                .findFirst()
                .orElse(null);
        if (primary != null) {
            return prefix + primary.description();
        }
        if (status == TraceExplanation.Status.PARTIAL) {
            return prefix + "Structural critical-path evidence is partial; review the listed evidence limitations.";
        }
        return prefix + "No dominant structural critical-path contributor or error was observed.";
    }

    private static TraceFindingEvidence evidence(
            SpanEntity span,
            CriticalPathSpanContribution contribution,
            SpanEntity related,
            Integer contributionPercentage,
            Long overlapDurationMs,
            String relationship
    ) {
        return new TraceFindingEvidence(
                span != null ? span.getSpanId() : contribution != null ? contribution.spanId() : null,
                span != null ? span.getParentSpanId() : contribution != null ? contribution.parentSpanId() : null,
                span != null ? span.getServiceName() : contribution != null ? contribution.serviceName() : null,
                span != null ? span.getName() : contribution != null ? contribution.operationName() : null,
                related != null ? related.getSpanId() : null,
                related != null ? related.getServiceName() : null,
                related != null ? related.getName() : null,
                span != null ? span.getDurationMs() : contribution != null ? contribution.durationMs() : null,
                contribution != null ? contribution.selfTimeMs() : null,
                contribution != null ? contribution.contributionDurationMs() : null,
                contributionPercentage,
                overlapDurationMs,
                span != null ? span.getStatusCode() : null,
                span != null ? span.getHttpStatusCode() : null,
                relationship
        );
    }

    private static TraceExplanation.Status explanationStatus(CriticalPathResult criticalPath) {
        if (criticalPath == null || criticalPath.status() == CriticalPathResult.Status.UNAVAILABLE) {
            return TraceExplanation.Status.INSUFFICIENT_EVIDENCE;
        }
        return criticalPath.status() == CriticalPathResult.Status.PARTIAL
                ? TraceExplanation.Status.PARTIAL
                : TraceExplanation.Status.COMPLETE;
    }

    private static boolean hasStructuralPath(CriticalPathResult criticalPath) {
        return criticalPath != null && criticalPath.status() != CriticalPathResult.Status.UNAVAILABLE;
    }

    private static Set<String> criticalSpanIds(CriticalPathResult criticalPath) {
        if (!hasStructuralPath(criticalPath)) {
            return Set.of();
        }
        Set<String> ids = new HashSet<>();
        criticalPath.spans().forEach(span -> ids.add(span.spanId()));
        return Set.copyOf(ids);
    }

    private static boolean isError(SpanEntity span) {
        return span != null && ("ERROR".equalsIgnoreCase(span.getStatusCode())
                || span.getHttpStatusCode() != null && span.getHttpStatusCode() >= 500);
    }

    private static boolean differentServices(String left, String right) {
        return left != null && !left.isBlank() && right != null && !right.isBlank() && !left.equals(right);
    }

    private static EvidenceStrength directStrength(CriticalPathResult criticalPath) {
        return criticalPath.status() == CriticalPathResult.Status.COMPLETE
                ? EvidenceStrength.HIGH : EvidenceStrength.LOW;
    }

    private static EvidenceStrength derivedStrength(CriticalPathResult criticalPath) {
        return criticalPath.status() == CriticalPathResult.Status.COMPLETE
                ? EvidenceStrength.MEDIUM : EvidenceStrength.LOW;
    }

    private static int findingPriority(String code) {
        return switch (code) {
            case "ERROR_ON_CRITICAL_PATH" -> 10;
            case "ERROR_OUTSIDE_STRUCTURAL_PATH" -> 15;
            case "DOWNSTREAM_ERROR" -> 20;
            case "CRITICAL_PATH_CONCENTRATION" -> 30;
            case "CROSS_SERVICE_CRITICAL_PATH" -> 40;
            case "LARGE_EXCLUSIVE_TIME" -> 50;
            case "OVERLAPPING_CHILD_WORK" -> 60;
            case "INCOMPLETE_STRUCTURAL_EVIDENCE" -> 70;
            default -> 100;
        };
    }

    private static String primarySpanId(TraceFinding finding) {
        return finding.evidence().isEmpty() || finding.evidence().getFirst().spanId() == null
                ? "" : finding.evidence().getFirst().spanId();
    }

    private static int percentage(long value, long total) {
        return total > 0 ? (int) Math.min(100L, Math.round(value * 100.0 / total)) : 0;
    }

    private static long millisBetween(LocalDateTime start, LocalDateTime end) {
        try {
            return Math.max(0L, Duration.between(start, end).toMillis());
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }

    private static String label(SpanEntity span) {
        return span != null ? label(span.getServiceName(), span.getName()) : "unknown span";
    }

    private static String label(String serviceName, String operationName) {
        String service = serviceName != null && !serviceName.isBlank() ? serviceName : "unknown-service";
        String operation = operationName != null && !operationName.isBlank() ? operationName : "unnamed-span";
        return service + " / " + operation;
    }

    private static String spanId(SpanEntity span) {
        return span != null && span.getSpanId() != null ? span.getSpanId() : "";
    }

    private static String readableCode(String code) {
        return code == null ? "unknown limitation" : code.toLowerCase().replace('_', ' ');
    }

    private record OverlapCandidate(SpanEntity parent, SpanEntity first, SpanEntity second, long overlapMs) {
    }
}
