package com.argusiq.tracing.explanation;

import com.argusiq.tracing.criticalpath.CriticalPathResult;
import com.argusiq.tracing.criticalpath.TraceCriticalPathCalculator;
import com.argusiq.tracing.entity.SpanEntity;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TraceExplanationEngineTest {

    private static final LocalDateTime BASE = LocalDateTime.of(2026, 1, 1, 0, 0);
    private static final String TRACE_ID = "trace-explanation";

    private final TraceCriticalPathCalculator criticalPathCalculator = new TraceCriticalPathCalculator();
    private final TraceExplanationEngine engine = new TraceExplanationEngine();

    @Test
    void healthySingleSpanTraceProducesNoFabricatedProblem() {
        TraceExplanation explanation = explain(List.of(span("root", null, 0, 1_000, "api", "OK")), "root");

        assertEquals(TraceExplanation.Status.COMPLETE, explanation.status());
        assertTrue(explanation.findings().isEmpty());
        assertTrue(explanation.summary().contains("No dominant structural critical-path contributor or error was observed"));
    }

    @Test
    void dominantCriticalPathSpanProducesConcentrationFinding() {
        TraceExplanation explanation = explain(List.of(
                span("root", null, 0, 1_000, "gateway", "OK"),
                span("charge", "root", 100, 750, "payment", "OK")
        ), "root");

        TraceFinding finding = finding(explanation, "CRITICAL_PATH_CONCENTRATION");
        assertEquals("charge", finding.evidence().getFirst().spanId());
        assertEquals(650L, finding.evidence().getFirst().contributionDurationMs());
        assertEquals(65, finding.evidence().getFirst().contributionPercentage());
    }

    @Test
    void trivialContributionDoesNotProduceConcentrationNoise() {
        TraceExplanation explanation = explain(List.of(
                span("root", null, 0, 180, "api", "OK"),
                span("child", "root", 0, 90, "worker", "OK")
        ), "root");

        assertFalse(hasFinding(explanation, "CRITICAL_PATH_CONCENTRATION"));
    }

    @Test
    void errorOnCriticalPathProducesHighValueFinding() {
        TraceExplanation explanation = explain(List.of(
                span("root", null, 0, 1_000, "gateway", "OK"),
                span("reserve", "root", 100, 800, "inventory", "ERROR")
        ), "root");

        TraceFinding finding = finding(explanation, "ERROR_ON_CRITICAL_PATH");
        assertEquals(TraceFinding.Significance.HIGH, finding.significance());
        assertEquals(TraceFinding.EvidenceStrength.HIGH, finding.evidenceStrength());
        assertEquals("reserve", finding.evidence().getFirst().spanId());
    }

    @Test
    void httpFiveHundredIsDirectErrorEvidence() {
        SpanEntity failed = span("call", "root", 100, 800, "orders", "UNSET");
        failed.setHttpStatusCode(503);
        TraceExplanation explanation = explain(List.of(
                span("root", null, 0, 1_000, "gateway", "OK"), failed
        ), "root");

        TraceFinding finding = finding(explanation, "ERROR_ON_CRITICAL_PATH");
        assertEquals(503, finding.evidence().getFirst().httpStatusCode());
    }

    @Test
    void downstreamErrorOutsideSelectedPathIsSurfaced() {
        TraceExplanation explanation = explain(List.of(
                span("root", null, 0, 1_000, "checkout", "OK"),
                span("dominant", "root", 100, 800, "payment", "OK"),
                span("failed", "root", 200, 300, "inventory", "ERROR")
        ), "root");

        TraceFinding finding = finding(explanation, "DOWNSTREAM_ERROR");
        assertEquals("failed", finding.evidence().getFirst().spanId());
        assertEquals("root", finding.evidence().getFirst().relatedSpanId());
        assertEquals("VALIDATED_CHILD_OF", finding.evidence().getFirst().relationship());
        assertEquals(TraceFinding.EvidenceStrength.MEDIUM, finding.evidenceStrength());
        assertTrue(finding.description().contains("beneath checkout / operation-root"));
    }

    @Test
    void missingParentErrorRemainsObservedWithoutDownstreamClaim() {
        TraceExplanation explanation = explain(List.of(
                span("root", null, 0, 1_000, "gateway", "OK"),
                span("failed", "missing", 100, 400, "inventory", "ERROR")
        ), "root");

        TraceFinding observed = finding(explanation, "ERROR_OUTSIDE_STRUCTURAL_PATH");
        assertEquals("DIRECT_OBSERVATION", observed.evidence().getFirst().relationship());
        assertEquals(TraceFinding.EvidenceStrength.HIGH, observed.evidenceStrength());
        assertFalse(hasFinding(explanation, "DOWNSTREAM_ERROR"));
        assertFalse(observed.description().contains("beneath"));
    }

    @Test
    void outOfBoundsErrorRemainsObservedWithoutDownstreamClaim() {
        TraceExplanation explanation = explain(List.of(
                span("root", null, 0, 1_000, "gateway", "OK"),
                span("failed", "root", 900, 1_100, "inventory", "ERROR")
        ), "root");

        assertTrue(hasFinding(explanation, "ERROR_OUTSIDE_STRUCTURAL_PATH"));
        assertFalse(hasFinding(explanation, "DOWNSTREAM_ERROR"));
    }

    @Test
    void cyclicErrorRemainsObservedWithoutDownstreamClaim() {
        TraceExplanation explanation = explain(List.of(
                span("root", null, 0, 1_000, "gateway", "OK"),
                span("a", "b", 100, 400, "inventory", "ERROR"),
                span("b", "a", 100, 400, "worker", "OK")
        ), "root");

        assertTrue(hasFinding(explanation, "ERROR_OUTSIDE_STRUCTURAL_PATH"));
        assertFalse(hasFinding(explanation, "DOWNSTREAM_ERROR"));
    }

    @Test
    void disconnectedMalformedErrorRemainsObservedWithoutRelationshipClaim() {
        TraceExplanation explanation = explain(List.of(
                span("root", null, 0, 1_000, "gateway", "OK"),
                span("other-root", null, 100, 900, "batch", "OK"),
                span("failed", "other-root", 700, 600, "inventory", "ERROR")
        ), "root");

        TraceFinding observed = finding(explanation, "ERROR_OUTSIDE_STRUCTURAL_PATH");
        assertEquals("DIRECT_OBSERVATION", observed.evidence().getFirst().relationship());
        assertFalse(hasFinding(explanation, "DOWNSTREAM_ERROR"));
        assertFalse(hasFinding(explanation, "OVERLAPPING_CHILD_WORK"));
    }

    @Test
    void crossServiceCriticalPathTransitionUsesValidatedParentRelationship() {
        TraceExplanation explanation = explain(List.of(
                span("root", null, 0, 1_000, "checkout", "OK"),
                span("charge", "root", 100, 700, "payment", "OK")
        ), "root");

        TraceFinding finding = finding(explanation, "CROSS_SERVICE_CRITICAL_PATH");
        assertEquals("charge", finding.evidence().getFirst().spanId());
        assertEquals("root", finding.evidence().getFirst().relatedSpanId());
        assertEquals("VALIDATED_CHILD_OF", finding.evidence().getFirst().relationship());
        assertTrue(finding.description().contains("validated parent/child transition"));
    }

    @Test
    void invalidCrossServiceTimingDoesNotProduceTransitionFinding() {
        TraceExplanation explanation = explain(List.of(
                span("root", null, 0, 1_000, "checkout", "OK"),
                span("charge", "root", 100, 1_100, "payment", "OK")
        ), "root");

        assertFalse(hasFinding(explanation, "CROSS_SERVICE_CRITICAL_PATH"));
    }

    @Test
    void dominantSelfTimeIsReportedWithoutResourceDiagnosis() {
        TraceExplanation explanation = explain(List.of(
                span("root", null, 0, 1_000, "api", "OK"),
                span("child", "root", 100, 400, "api", "OK")
        ), "root");

        TraceFinding finding = finding(explanation, "LARGE_EXCLUSIVE_TIME");
        assertEquals(700L, finding.evidence().getFirst().selfTimeMs());
        String wording = finding.description().toLowerCase();
        assertFalse(wording.contains("cpu"));
        assertFalse(wording.contains("thread"));
        assertFalse(wording.contains("application problem"));
    }

    @Test
    void meaningfulParallelWorkExplainsNonAdditiveDurations() {
        TraceExplanation explanation = explain(List.of(
                span("root", null, 0, 1_000, "api", "OK"),
                span("first", "root", 100, 700, "worker-a", "OK"),
                span("second", "root", 200, 600, "worker-b", "OK")
        ), "root");

        TraceFinding finding = finding(explanation, "OVERLAPPING_CHILD_WORK");
        assertEquals(400L, finding.evidence().getFirst().overlapDurationMs());
        assertTrue(finding.description().contains("should not be summed"));
    }

    @Test
    void partialCriticalPathPropagatesPartialExplanationQuality() {
        TraceExplanation explanation = explain(List.of(
                span("orphan", "missing", 100, 500, "worker", "OK")
        ), "orphan");

        assertEquals(TraceExplanation.Status.PARTIAL, explanation.status());
        assertTrue(hasFinding(explanation, "INCOMPLETE_STRUCTURAL_EVIDENCE"));
    }

    @Test
    void unavailableCriticalPathDoesNotFabricatePathFindings() {
        TraceExplanation explanation = explain(List.of(
                span("a", "b", 0, 100, "one", "OK"),
                span("b", "a", 0, 100, "two", "OK")
        ), "a");

        assertEquals(TraceExplanation.Status.INSUFFICIENT_EVIDENCE, explanation.status());
        assertFalse(hasFinding(explanation, "CRITICAL_PATH_CONCENTRATION"));
        assertFalse(hasFinding(explanation, "CROSS_SERVICE_CRITICAL_PATH"));
        assertFalse(hasFinding(explanation, "LARGE_EXCLUSIVE_TIME"));
    }

    @Test
    void missingParentIssueHasReadableLimitation() {
        TraceExplanation explanation = explain(List.of(
                span("orphan", "missing", 100, 500, "worker", "OK")
        ), "orphan");

        assertEquals("At least one span references a parent that is not present.",
                limitation(explanation, "MISSING_PARENT").description());
    }

    @Test
    void multipleRootsIssueHasReadableLimitation() {
        TraceExplanation explanation = explain(List.of(
                span("preferred", null, 0, 500, "one", "OK"),
                span("other", null, 0, 900, "two", "OK")
        ), "preferred");

        assertEquals("Multiple root candidates are present in the trace.",
                limitation(explanation, "MULTIPLE_ROOT_CANDIDATES").description());
    }

    @Test
    void malformedTimestampEvidenceIsHandledSafely() {
        TraceExplanation explanation = assertDoesNotThrow(() -> explain(List.of(
                span("bad", null, 500, 100, "worker", "OK")
        ), "bad"));

        assertEquals(TraceExplanation.Status.INSUFFICIENT_EVIDENCE, explanation.status());
        assertEquals("At least one span has malformed timestamps.",
                limitation(explanation, "MALFORMED_SPAN_TIMESTAMPS").description());
    }

    @Test
    void cyclicEvidenceIsHandledSafely() {
        TraceExplanation explanation = assertDoesNotThrow(() -> explain(List.of(
                span("a", "b", 0, 100, "one", "OK"),
                span("b", "a", 0, 100, "two", "OK")
        ), "a"));

        assertEquals("A cycle exists in the reported parent relationships.",
                limitation(explanation, "CYCLIC_PARENT_GRAPH").description());
    }

    @Test
    void multipleFindingsUseStablePriorityOrdering() {
        TraceExplanation explanation = explain(List.of(
                span("root", null, 0, 1_000, "checkout", "OK"),
                span("selected-error", "root", 100, 800, "payment", "ERROR"),
                span("other-error", "root", 200, 300, "inventory", "ERROR")
        ), "root");

        List<String> codes = explanation.findings().stream().map(TraceFinding::code).toList();
        assertTrue(codes.indexOf("ERROR_ON_CRITICAL_PATH") < codes.indexOf("DOWNSTREAM_ERROR"));
        assertTrue(codes.indexOf("DOWNSTREAM_ERROR") < codes.indexOf("CRITICAL_PATH_CONCENTRATION"));
        assertTrue(codes.indexOf("CRITICAL_PATH_CONCENTRATION") < codes.indexOf("CROSS_SERVICE_CRITICAL_PATH"));
    }

    @Test
    void summaryIsDeterministicAcrossInputOrder() {
        List<SpanEntity> spans = new ArrayList<>(List.of(
                span("root", null, 0, 1_000, "gateway", "OK"),
                span("charge", "root", 100, 750, "payment", "OK")
        ));
        TraceExplanation first = explain(spans, "root");
        Collections.reverse(spans);
        TraceExplanation second = explain(spans, "root");

        assertEquals(first.summary(), second.summary());
        assertEquals("Trace completed in 1000 ms. payment / operation-charge contributes 65% (650 ms) "
                + "of the structural critical-path duration.", first.summary());
    }

    @Test
    void multiServiceEvidenceReferencesCorrectServicesAndSpans() {
        TraceExplanation explanation = explain(List.of(
                span("root", null, 0, 1_000, "gateway", "OK"),
                span("charge", "root", 100, 750, "payment", "OK")
        ), "root");

        TraceFindingEvidence evidence = finding(explanation, "CROSS_SERVICE_CRITICAL_PATH").evidence().getFirst();
        assertEquals("charge", evidence.spanId());
        assertEquals("payment", evidence.serviceName());
        assertEquals("root", evidence.relatedSpanId());
        assertEquals("gateway", evidence.relatedServiceName());
    }

    @Test
    void partialStructuralFindingsAreMarkedLowStrength() {
        TraceExplanation explanation = explain(List.of(
                span("root", null, 0, 1_000, "gateway", "OK"),
                span("charge", "root", 100, 750, "payment", "OK"),
                span("other-root", null, 0, 400, "other", "OK")
        ), "root");

        assertEquals(TraceExplanation.Status.PARTIAL, explanation.status());
        assertEquals(TraceFinding.EvidenceStrength.LOW,
                finding(explanation, "CRITICAL_PATH_CONCENTRATION").evidenceStrength());
    }

    @Test
    void healthyMultiSpanTraceWithoutErrorOrDominanceRemainsQuiet() {
        TraceExplanation explanation = explain(List.of(
                span("root", null, 0, 240, "api", "OK"),
                span("one", "root", 0, 80, "api", "OK"),
                span("two", "root", 80, 160, "api", "OK"),
                span("three", "root", 160, 240, "api", "OK")
        ), "root");

        assertTrue(explanation.findings().isEmpty());
        assertTrue(explanation.summary().contains("No dominant structural critical-path contributor or error was observed"));
    }

    @Test
    void structuralModelLimitationIsAlwaysExplicit() {
        TraceExplanation explanation = explain(List.of(span("root", null, 0, 100, "api", "OK")), "root");

        assertTrue(limitation(explanation, "STRUCTURAL_MODEL_ONLY").description()
                .contains("do not prove synchronous blocking"));
    }

    private TraceExplanation explain(List<SpanEntity> spans, String preferredRootSpanId) {
        CriticalPathResult criticalPath = criticalPathCalculator.calculate(spans, preferredRootSpanId);
        return engine.explain(spans, criticalPath);
    }

    private SpanEntity span(
            String id,
            String parentId,
            long startMs,
            long endMs,
            String service,
            String status
    ) {
        return new SpanEntity(
                id,
                TRACE_ID,
                parentId,
                "operation-" + id,
                parentId == null ? "SERVER" : "INTERNAL",
                BASE.plusNanos(startMs * 1_000_000),
                BASE.plusNanos(endMs * 1_000_000),
                Math.max(0, endMs - startMs),
                status,
                "ERROR".equals(status) ? "observed failure" : null,
                service
        );
    }

    private TraceFinding finding(TraceExplanation explanation, String code) {
        return explanation.findings().stream()
                .filter(finding -> code.equals(finding.code()))
                .findFirst()
                .orElseThrow();
    }

    private boolean hasFinding(TraceExplanation explanation, String code) {
        return explanation.findings().stream().anyMatch(finding -> code.equals(finding.code()));
    }

    private TraceExplanationLimitation limitation(TraceExplanation explanation, String code) {
        return explanation.limitations().stream()
                .filter(limitation -> code.equals(limitation.code()))
                .findFirst()
                .orElseThrow();
    }
}
