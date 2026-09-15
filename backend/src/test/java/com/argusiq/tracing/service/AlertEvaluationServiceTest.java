package com.argusiq.tracing.service;

import com.argusiq.tracing.entity.AlertRule;
import com.argusiq.tracing.entity.SpanEntity;
import com.argusiq.tracing.entity.TraceEntity;
import com.argusiq.tracing.event.TelemetryChangedEvent;
import com.argusiq.tracing.repository.AlertRuleRepository;
import com.argusiq.tracing.repository.TelemetryAnalyticsRepository;
import com.argusiq.tracing.repository.TelemetryAnalyticsRepository.AlertWindowAggregate;
import com.argusiq.tracing.repository.TraceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertEvaluationServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-05T06:00:00Z");
    private static final LocalDateTime EVALUATION_TIME = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);

    @Mock
    private AlertRuleRepository ruleRepository;
    @Mock
    private TelemetryAnalyticsRepository analyticsRepository;
    @Mock
    private TraceRepository traceRepository;
    @Mock
    private AlertOccurrenceWriter occurrenceWriter;

    private AlertEvaluationService service;

    @BeforeEach
    void setup() {
        service = new AlertEvaluationService(
                ruleRepository, analyticsRepository, traceRepository, occurrenceWriter,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void disabledRuleNeverEvaluates() {
        AlertRule rule = rule(AlertEvaluationService.ERROR_RATE_THRESHOLD, 10.0, 20, null, false);

        service.evaluateRule(rule, new TelemetryChangedEvent("trace"), EVALUATION_TIME);

        verify(analyticsRepository, never()).alertWindowAggregate(any(), any(), any());
        verify(occurrenceWriter, never()).recordMatch(any(), any());
    }

    @Test
    void errorRateBelowThresholdDoesNotMatch() {
        AlertRule rule = rule(AlertEvaluationService.ERROR_RATE_THRESHOLD, 10.0, 10, null, true);
        when(analyticsRepository.alertWindowAggregate(any(), any(), any()))
                .thenReturn(new AlertWindowAggregate(20, 1, 100.0));

        service.evaluateRule(rule, null, EVALUATION_TIME);

        verify(occurrenceWriter, never()).recordMatch(any(), any());
        verify(occurrenceWriter).recordClear(1L, EVALUATION_TIME);
    }

    @Test
    void errorRateAtInclusiveThresholdMatchesWithExactEvidence() {
        AlertRule rule = rule(AlertEvaluationService.ERROR_RATE_THRESHOLD, 10.0, 20, null, true);
        when(analyticsRepository.alertWindowAggregate(any(), any(), any()))
                .thenReturn(new AlertWindowAggregate(20, 2, 100.0));

        AlertEvaluation evaluation = matchedEvaluation(rule);

        assertEquals(10.0, evaluation.evidence().observedValue());
        assertEquals(2L, evaluation.evidence().errorCount());
        assertEquals(20L, evaluation.evidence().sampleCount());
        assertEquals(EVALUATION_TIME.minusMinutes(5), evaluation.windowStart());
        assertEquals(EVALUATION_TIME, evaluation.windowEnd());
    }

    @Test
    void zeroDenominatorAndInsufficientSamplesDoNotMatch() {
        AlertRule rule = rule(AlertEvaluationService.ERROR_RATE_THRESHOLD, 0.0, 2, null, true);
        when(analyticsRepository.alertWindowAggregate(any(), any(), any()))
                .thenReturn(new AlertWindowAggregate(0, 0, null), new AlertWindowAggregate(1, 1, 10.0));

        service.evaluateRule(rule, null, EVALUATION_TIME);
        service.evaluateRule(rule, null, EVALUATION_TIME);

        verify(occurrenceWriter, never()).recordMatch(any(), any());
    }

    @Test
    void errorRateAboveThresholdMatches() {
        AlertRule rule = rule(AlertEvaluationService.ERROR_RATE_THRESHOLD, 10.0, 20, null, true);
        when(analyticsRepository.alertWindowAggregate(any(), any(), any()))
                .thenReturn(new AlertWindowAggregate(40, 8, 100.0));

        assertEquals(20.0, matchedEvaluation(rule).evidence().observedValue());
    }

    @Test
    void latencyBelowThresholdDoesNotMatch() {
        AlertRule rule = rule(AlertEvaluationService.P95_LATENCY_THRESHOLD, 500.0, 10, null, true);
        when(analyticsRepository.alertWindowAggregate(any(), any(), any()))
                .thenReturn(new AlertWindowAggregate(10, 0, 499.9));

        service.evaluateRule(rule, null, EVALUATION_TIME);

        verify(occurrenceWriter, never()).recordMatch(any(), any());
    }

    @Test
    void latencyAtThresholdAndAboveMatchInclusively() {
        AlertRule rule = rule(AlertEvaluationService.P95_LATENCY_THRESHOLD, 500.0, 10, null, true);
        when(analyticsRepository.alertWindowAggregate(any(), any(), any()))
                .thenReturn(new AlertWindowAggregate(10, 0, 500.0), new AlertWindowAggregate(10, 0, 780.0));

        assertEquals(500.0, matchedEvaluation(rule).evidence().observedValue());
        reset(occurrenceWriter);
        assertEquals(780.0, matchedEvaluation(rule).evidence().observedValue());
    }

    @Test
    void serviceScopeIsPassedExactlyAndGlobalScopeUsesNull() {
        AlertRule scoped = rule(AlertEvaluationService.ERROR_RATE_THRESHOLD, 10.0, 1, "inventory", true);
        when(analyticsRepository.alertWindowAggregate(any(), any(), eq("inventory")))
                .thenReturn(new AlertWindowAggregate(1, 1, null));
        service.evaluateRule(scoped, null, EVALUATION_TIME);
        verify(analyticsRepository).alertWindowAggregate(any(), any(), eq("inventory"));

        AlertRule global = rule(AlertEvaluationService.ERROR_RATE_THRESHOLD, 10.0, 1, null, true);
        when(analyticsRepository.alertWindowAggregate(any(), any(), eq(null)))
                .thenReturn(new AlertWindowAggregate(1, 1, null));
        service.evaluateRule(global, null, EVALUATION_TIME);
        verify(analyticsRepository).alertWindowAggregate(any(), any(), eq(null));
    }

    @Test
    void aggregateRulePopulationComesFromRuleScopeNotTriggeringTrace() {
        AlertRule paymentRule = rule(
                AlertEvaluationService.ERROR_RATE_THRESHOLD, 10.0, 1, "payment-service", true
        );
        when(ruleRepository.findByEnabledTrueOrderByIdAsc()).thenReturn(List.of(paymentRule));
        when(analyticsRepository.alertWindowAggregate(any(), any(), eq("payment-service")))
                .thenReturn(new AlertWindowAggregate(10, 2, 100.0));

        service.evaluate(new TelemetryChangedEvent("checkout-service-trace"));

        verify(analyticsRepository).alertWindowAggregate(
                EVALUATION_TIME.minusMinutes(5), EVALUATION_TIME, "payment-service"
        );
        verify(traceRepository, never()).findByTraceIdWithSpans(any());
        verify(occurrenceWriter).recordMatch(eq(1L), any());
    }

    @Test
    void traceErrorMatchesDirectObservationAndSuccessfulTraceDoesNot() {
        AlertRule rule = rule(AlertEvaluationService.TRACE_ERROR, 0.0, 1, "checkout", true);
        when(traceRepository.findByTraceIdWithSpans("failed")).thenReturn(Optional.of(trace("failed", "ERROR", 503)));

        AlertEvaluation evaluation = matchedEvaluation(rule, new TelemetryChangedEvent("failed"));

        assertEquals("TRACE_STATUS_ERROR", evaluation.evidence().metric());
        assertEquals("failed", evaluation.evidence().traceId());
        assertEquals(503, evaluation.evidence().httpStatus());
        assertEquals(EVALUATION_TIME, evaluation.evidence().observedAt());
        assertEquals(null, evaluation.evidence().windowEnd());

        reset(occurrenceWriter);
        when(traceRepository.findByTraceIdWithSpans("ok")).thenReturn(Optional.of(trace("ok", "OK", 200)));
        service.evaluateRule(rule, new TelemetryChangedEvent("ok"), EVALUATION_TIME);
        verify(occurrenceWriter, never()).recordMatch(any(), any());
    }

    @Test
    void traceRuleExcludesOtherServices() {
        AlertRule rule = rule(AlertEvaluationService.TRACE_ERROR, 0.0, 1, "inventory", true);
        when(traceRepository.findByTraceIdWithSpans("failed")).thenReturn(Optional.of(trace("failed", "ERROR", 500)));

        service.evaluateRule(rule, new TelemetryChangedEvent("failed"), EVALUATION_TIME);

        verify(occurrenceWriter, never()).recordMatch(any(), any());
    }

    @Test
    void oneCapturedEvaluationTimeIsUsedForAllRules() {
        AlertRule first = rule(AlertEvaluationService.ERROR_RATE_THRESHOLD, 10.0, 1, null, true);
        AlertRule second = rule(AlertEvaluationService.P95_LATENCY_THRESHOLD, 10.0, 1, null, true);
        when(ruleRepository.findByEnabledTrueOrderByIdAsc()).thenReturn(List.of(first, second));
        when(analyticsRepository.alertWindowAggregate(any(), any(), any()))
                .thenReturn(new AlertWindowAggregate(1, 1, 20.0));

        service.evaluate(new TelemetryChangedEvent("trace"));

        ArgumentCaptor<AlertEvaluation> captor = ArgumentCaptor.forClass(AlertEvaluation.class);
        verify(occurrenceWriter, org.mockito.Mockito.times(2)).recordMatch(any(), captor.capture());
        assertEquals(EVALUATION_TIME, captor.getAllValues().get(0).evaluationTime());
        assertEquals(EVALUATION_TIME, captor.getAllValues().get(1).evaluationTime());
    }

    @Test
    void failingRuleDoesNotSuppressLaterMatchOrClearEvaluations() {
        AlertRule failing = rule(1L, "broken", "UNSUPPORTED", 0.0, 1, null, true);
        AlertRule matching = rule(2L, "matching", AlertEvaluationService.ERROR_RATE_THRESHOLD,
                10.0, 1, null, true);
        AlertRule clearing = rule(3L, "clearing", AlertEvaluationService.P95_LATENCY_THRESHOLD,
                500.0, 1, null, true);
        when(ruleRepository.findByEnabledTrueOrderByIdAsc()).thenReturn(List.of(failing, matching, clearing));
        when(analyticsRepository.alertWindowAggregate(any(), any(), any()))
                .thenReturn(new AlertWindowAggregate(10, 2, 100.0))
                .thenReturn(new AlertWindowAggregate(10, 0, 499.0));

        service.evaluate(new TelemetryChangedEvent("trace"));

        verify(occurrenceWriter, never()).recordMatch(eq(1L), any());
        verify(occurrenceWriter, never()).recordClear(eq(1L), any());
        verify(occurrenceWriter).recordMatch(eq(2L), any());
        verify(occurrenceWriter).recordClear(3L, EVALUATION_TIME);
    }

    private AlertEvaluation matchedEvaluation(AlertRule rule) {
        return matchedEvaluation(rule, null);
    }

    private AlertEvaluation matchedEvaluation(AlertRule rule, TelemetryChangedEvent event) {
        service.evaluateRule(rule, event, EVALUATION_TIME);
        ArgumentCaptor<AlertEvaluation> captor = ArgumentCaptor.forClass(AlertEvaluation.class);
        verify(occurrenceWriter).recordMatch(eq(1L), captor.capture());
        return captor.getValue();
    }

    private AlertRule rule(String type, double threshold, long minimumSamples, String serviceName, boolean enabled) {
        return rule(1L, "test", type, threshold, minimumSamples, serviceName, enabled);
    }

    private AlertRule rule(
            long id,
            String name,
            String type,
            double threshold,
            long minimumSamples,
            String serviceName,
            boolean enabled
    ) {
        AlertRule rule = mock(AlertRule.class);
        lenient().when(rule.getId()).thenReturn(id);
        lenient().when(rule.getName()).thenReturn(name);
        lenient().when(rule.getType()).thenReturn(type);
        lenient().when(rule.getThreshold()).thenReturn(threshold);
        lenient().when(rule.getWindowSeconds()).thenReturn(300L);
        lenient().when(rule.getMinimumSamples()).thenReturn(minimumSamples);
        lenient().when(rule.getServiceName()).thenReturn(serviceName);
        lenient().when(rule.isEnabled()).thenReturn(enabled);
        return rule;
    }

    private TraceEntity trace(String traceId, String status, int httpStatus) {
        TraceEntity trace = new TraceEntity(
                traceId, "checkout", "POST /checkout", EVALUATION_TIME.minusSeconds(1), EVALUATION_TIME,
                1000L, status, null, "POST", "/checkout"
        );
        SpanEntity root = new SpanEntity(
                "root", traceId, null, "POST /checkout", "SERVER", EVALUATION_TIME.minusSeconds(1),
                EVALUATION_TIME, 1000L, status, null, "checkout"
        );
        root.setHttpStatusCode(httpStatus);
        trace.addSpan(root);
        trace.setRootSpanId("root");
        return trace;
    }
}
