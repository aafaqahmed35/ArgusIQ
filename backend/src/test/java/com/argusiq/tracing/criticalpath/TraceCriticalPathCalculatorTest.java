package com.argusiq.tracing.criticalpath;

import com.argusiq.tracing.entity.SpanEntity;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TraceCriticalPathCalculatorTest {

    private static final LocalDateTime BASE = LocalDateTime.of(2026, 1, 1, 0, 0);
    private static final String TRACE_ID = "trace-1";

    private final TraceCriticalPathCalculator calculator = new TraceCriticalPathCalculator();

    @Test
    void singleSpanTraceUsesItsExclusiveWallClockInterval() {
        CriticalPathResult result = calculate(span("root", null, 0, 100));

        assertEquals(CriticalPathResult.Status.COMPLETE, result.status());
        assertEquals(100, result.totalDurationMs());
        assertEquals(100, result.traceWallClockDurationMs());
        assertEquals(100, result.sumSpanDurationsMs());
        assertEquals(List.of("root"), ids(result));
        assertEquals(100, result.spans().getFirst().contributionDurationMs());
    }

    @Test
    void simpleParentChildChainAttributesExclusiveTimeWithoutDoubleCounting() {
        CriticalPathResult result = calculate(
                span("root", null, 0, 1_000),
                span("child", "root", 100, 900)
        );

        assertEquals(1_000, result.totalDurationMs());
        assertEquals(1_800, result.sumSpanDurationsMs());
        assertEquals(List.of("root", "child"), ids(result));
        assertEquals(List.of(200L, 800L), contributions(result));
    }

    @Test
    void nestedSpansContributeOnlyTheirExclusiveIntervals() {
        CriticalPathResult result = calculate(
                span("root", null, 0, 1_000),
                span("child", "root", 100, 900),
                span("grandchild", "child", 200, 800)
        );

        assertEquals(1_000, result.totalDurationMs());
        assertEquals(List.of("root", "child", "grandchild"), ids(result));
        assertEquals(List.of(200L, 200L, 600L), contributions(result));
    }

    @Test
    void nonOverlappingSiblingsBothBelongToTheCausalSequence() {
        CriticalPathResult result = calculate(
                span("root", null, 0, 1_000),
                span("first", "root", 100, 300),
                span("second", "root", 400, 700)
        );

        assertEquals(1_000, result.totalDurationMs());
        assertEquals(List.of("root", "first", "second"), ids(result));
        assertEquals(List.of(500L, 200L, 300L), contributions(result));
    }

    @Test
    void overlappingSiblingsCompeteInsteadOfBeingSummed() {
        CriticalPathResult result = calculate(
                span("root", null, 0, 1_000),
                span("short-early", "root", 50, 200),
                span("dominant", "root", 100, 700)
        );

        assertEquals(950, result.totalDurationMs());
        assertEquals(1_750, result.sumSpanDurationsMs());
        assertEquals(1_000, result.longestSpanDurationMs());
        assertEquals(List.of("root", "dominant"), ids(result));
        assertEquals(List.of(350L, 600L), contributions(result));
    }

    @Test
    void containedOverlappingSiblingExampleDoesNotDoubleCount() {
        CriticalPathResult result = calculate(
                span("root", null, 0, 1_000),
                span("a", "root", 100, 700),
                span("b", "root", 200, 400)
        );

        assertEquals(1_000, result.totalDurationMs());
        assertEquals(1_800, result.sumSpanDurationsMs());
        assertEquals(List.of("root", "a"), ids(result));
        assertEquals(List.of(400L, 600L), contributions(result));
    }

    @Test
    void deeplyNestedOverlapSelectsTheDominantDescendantPath() {
        CriticalPathResult result = calculate(
                span("root", null, 0, 1_000),
                span("orchestrator", "root", 100, 900),
                span("early", "orchestrator", 150, 400),
                span("dominant", "orchestrator", 300, 800)
        );

        assertEquals(850, result.totalDurationMs());
        assertEquals(List.of("root", "orchestrator", "dominant"), ids(result));
        assertEquals(List.of(200L, 150L, 500L), contributions(result));
    }

    @Test
    void longestIndividualSpanIsNotTheCriticalPathModel() {
        CriticalPathResult result = calculate(
                span("root", null, 0, 1_000),
                span("short-early", "root", 50, 200),
                span("dominant", "root", 100, 700)
        );

        assertEquals(1_000, result.longestSpanDurationMs());
        assertEquals(950, result.totalDurationMs());
        assertFalse(ids(result).contains("short-early"));
    }

    @Test
    void evidencePreservesServicesAcrossOneCausalChain() {
        SpanEntity root = span("root", null, 0, 1_000, "gateway");
        SpanEntity client = span("client", "root", 100, 900, "orders");
        SpanEntity database = span("database", "client", 200, 800, "postgres");

        CriticalPathResult result = calculate(root, client, database);

        assertEquals(List.of("gateway", "orders", "postgres"),
                result.spans().stream().map(CriticalPathSpanContribution::serviceName).toList());
    }

    @Test
    void missingParentProducesAnHonestPartialComponentResult() {
        CriticalPathResult result = calculator.calculate(
                List.of(span("orphan", "missing", 100, 500)),
                "orphan"
        );

        assertEquals(CriticalPathResult.Status.PARTIAL, result.status());
        assertEquals(400, result.totalDurationMs());
        assertTrue(result.issues().contains("MISSING_PARENT"));
        assertEquals(List.of("orphan"), ids(result));
    }

    @Test
    void multipleRootsUsePreferredComponentAndReportPartialEvidence() {
        CriticalPathResult result = calculator.calculate(
                List.of(span("preferred", null, 0, 500), span("other", null, 0, 900)),
                "preferred"
        );

        assertEquals(CriticalPathResult.Status.PARTIAL, result.status());
        assertEquals(500, result.totalDurationMs());
        assertTrue(result.issues().contains("MULTIPLE_ROOT_CANDIDATES"));
        assertTrue(result.issues().contains("DISCONNECTED_SPANS"));
        assertEquals(List.of("preferred"), ids(result));
    }

    @Test
    void zeroDurationSpanIsValidAndNeverProducesNegativeTime() {
        CriticalPathResult result = calculate(span("root", null, 100, 100));

        assertEquals(CriticalPathResult.Status.COMPLETE, result.status());
        assertEquals(0, result.totalDurationMs());
        assertEquals(0, result.spans().getFirst().selfTimeMs());
        assertEquals(0, result.spans().getFirst().contributionDurationMs());
    }

    @Test
    void cyclicParentGraphIsUnavailableAndDoesNotHang() {
        CriticalPathResult result = calculator.calculate(
                List.of(span("a", "b", 0, 100), span("b", "a", 0, 100)),
                "a"
        );

        assertEquals(CriticalPathResult.Status.UNAVAILABLE, result.status());
        assertEquals(0, result.totalDurationMs());
        assertTrue(result.issues().contains("CYCLIC_PARENT_GRAPH"));
        assertTrue(result.spans().isEmpty());
    }

    @Test
    void childOutsideParentBoundsIsExcludedRatherThanClipped() {
        CriticalPathResult result = calculate(
                span("root", null, 100, 500),
                span("outside", "root", 0, 600)
        );

        assertEquals(CriticalPathResult.Status.PARTIAL, result.status());
        assertEquals(400, result.totalDurationMs());
        assertEquals(List.of("root"), ids(result));
        assertTrue(result.issues().contains("CHILD_OUTSIDE_PARENT_BOUNDS"));
    }

    @Test
    void malformedTimestampIsNotConvertedIntoCausalEvidence() {
        CriticalPathResult result = calculate(span("bad", null, 500, 100));

        assertEquals(CriticalPathResult.Status.UNAVAILABLE, result.status());
        assertEquals(0, result.totalDurationMs());
        assertTrue(result.issues().contains("MALFORMED_SPAN_TIMESTAMPS"));
    }

    @Test
    void duplicateIdentityIsUnavailableInsteadOfDependingOnInputOrder() {
        CriticalPathResult result = calculate(
                span("same", null, 0, 100),
                span("same", null, 0, 200)
        );

        assertEquals(CriticalPathResult.Status.UNAVAILABLE, result.status());
        assertTrue(result.issues().contains("DUPLICATE_SPAN_ID"));
    }

    @Test
    void deterministicTieKeepsTheEarlierFinishingCandidate() {
        CriticalPathResult result = calculate(
                span("root", null, 0, 1_000),
                span("a", "root", 100, 500),
                span("b", "root", 200, 600)
        );

        assertEquals(List.of("root", "a"), ids(result));
    }

    @Test
    void veryDeepTraceUsesIterativeGraphWalks() {
        List<SpanEntity> spans = new ArrayList<>();
        spans.add(span("span-0", null, 0, 5_000));
        for (int index = 1; index < 5_000; index++) {
            spans.add(span("span-" + index, "span-" + (index - 1), index, 5_000));
        }

        CriticalPathResult result = calculator.calculate(spans, "span-0");

        assertEquals(CriticalPathResult.Status.COMPLETE, result.status());
        assertEquals(5_000, result.totalDurationMs());
        assertEquals(5_000, result.spans().size());
    }

    private CriticalPathResult calculate(SpanEntity... spans) {
        return calculator.calculate(List.of(spans), spans.length > 0 ? spans[0].getSpanId() : null);
    }

    private SpanEntity span(String id, String parentId, long startMs, long endMs) {
        return span(id, parentId, startMs, endMs, "service");
    }

    private SpanEntity span(String id, String parentId, long startMs, long endMs, String service) {
        return new SpanEntity(
                id,
                TRACE_ID,
                parentId,
                "operation-" + id,
                parentId == null ? "SERVER" : "INTERNAL",
                BASE.plusNanos(startMs * 1_000_000),
                BASE.plusNanos(endMs * 1_000_000),
                Math.max(0, endMs - startMs),
                "OK",
                null,
                service
        );
    }

    private List<String> ids(CriticalPathResult result) {
        return result.spans().stream().map(CriticalPathSpanContribution::spanId).toList();
    }

    private List<Long> contributions(CriticalPathResult result) {
        return result.spans().stream().map(CriticalPathSpanContribution::contributionDurationMs).toList();
    }
}
