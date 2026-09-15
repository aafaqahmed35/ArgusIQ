package com.argusiq.tracing.service;

import com.argusiq.AbstractArgusIqIntegrationTest;
import com.argusiq.tracing.dto.AlertEvidence;
import com.argusiq.tracing.dto.AlertResponse;
import com.argusiq.tracing.dto.AlertRuleRequest;
import com.argusiq.tracing.entity.AlertRule;
import com.argusiq.tracing.entity.SpanEntity;
import com.argusiq.tracing.entity.TraceEntity;
import com.argusiq.tracing.event.TelemetryChangedEvent;
import com.argusiq.tracing.repository.AlertRepository;
import com.argusiq.tracing.repository.AlertRuleRepository;
import com.argusiq.tracing.repository.TraceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AlertOccurrenceIntegrationTest extends AbstractArgusIqIntegrationTest {

    private static final LocalDateTime BASE = LocalDateTime.of(2026, 9, 5, 6, 0);

    @Autowired
    private AlertRepository alertRepository;
    @Autowired
    private AlertRuleRepository ruleRepository;
    @Autowired
    private AlertService alertService;
    @Autowired
    private AlertOccurrenceWriter occurrenceWriter;
    @Autowired
    private TraceRepository traceRepository;
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        alertRepository.deleteAll();
        ruleRepository.deleteAll();
        traceRepository.deleteAll();
    }

    @Test
    void repeatedViolationUpdatesOneOccurrenceAndStructuredEvidence() {
        AlertRule rule = createRule(AlertEvaluationService.ERROR_RATE_THRESHOLD);

        occurrenceWriter.recordMatch(rule.getId(), evaluation(BASE, 20.0, 40, 8));
        occurrenceWriter.recordMatch(rule.getId(), evaluation(BASE.plusMinutes(1), 25.0, 40, 10));

        assertEquals(1, alertRepository.count());
        AlertResponse response = alertService.getAlerts().getFirst();
        assertEquals(BASE, response.firstTriggeredAt());
        assertEquals(BASE.plusMinutes(1), response.lastTriggeredAt());
        assertEquals(25.0, response.evidence().observedValue());
        assertEquals(10L, response.evidence().errorCount());
        assertEquals("MATCHED", response.lastEvaluationState());
    }

    @Test
    void acknowledgementPreservesActiveStateAndResolutionAllowsRetrigger() {
        AlertRule rule = createRule(AlertEvaluationService.ERROR_RATE_THRESHOLD);
        occurrenceWriter.recordMatch(rule.getId(), evaluation(BASE, 20.0, 20, 4));
        Long firstId = alertService.getAlerts().getFirst().alertId();

        AlertResponse acknowledged = alertService.acknowledgeAlert(firstId).orElseThrow();
        assertTrue(acknowledged.acknowledged());
        assertEquals("ACKNOWLEDGED", acknowledged.status());
        assertNotNull(acknowledged.acknowledgedAt());

        occurrenceWriter.recordMatch(rule.getId(), evaluation(BASE.plusMinutes(1), 30.0, 20, 6));
        assertEquals(1, alertRepository.count());
        assertEquals("ACKNOWLEDGED", alertService.getAlert(firstId).orElseThrow().status());

        AlertResponse resolved = alertService.resolveAlert(firstId).orElseThrow();
        assertEquals("RESOLVED", resolved.status());
        assertNotNull(resolved.resolvedAt());
        assertNull(alertRepository.findById(firstId).orElseThrow().getActiveKey());

        occurrenceWriter.recordMatch(rule.getId(), evaluation(BASE.plusMinutes(2), 35.0, 20, 7));
        assertEquals(2, alertRepository.count());
        assertFalse(alertService.getAlerts().getFirst().alertId().equals(firstId));
    }

    @Test
    void clearEvaluationPreservesOccurrenceForManualResolution() {
        AlertRule rule = createRule(AlertEvaluationService.ERROR_RATE_THRESHOLD);
        occurrenceWriter.recordMatch(rule.getId(), evaluation(BASE, 20.0, 20, 4));

        occurrenceWriter.recordClear(rule.getId(), BASE.plusMinutes(1));

        AlertResponse response = alertService.getAlerts().getFirst();
        assertEquals("OPEN", response.status());
        assertEquals("CLEAR", response.lastEvaluationState());
        assertEquals(BASE.plusMinutes(1), response.evaluationTime());
    }

    @Test
    void clearedAcknowledgedOccurrenceReturnsToMatchBeforeResolutionThenRetriggersAfterResolution() {
        AlertRule rule = createRule(AlertEvaluationService.ERROR_RATE_THRESHOLD);
        occurrenceWriter.recordMatch(rule.getId(), evaluation(BASE, 20.0, 20, 4));
        Long firstId = alertService.getAlerts().getFirst().alertId();
        alertService.acknowledgeAlert(firstId).orElseThrow();

        occurrenceWriter.recordClear(rule.getId(), BASE.plusMinutes(1));
        occurrenceWriter.recordMatch(rule.getId(), evaluation(BASE.plusMinutes(2), 30.0, 20, 6));

        AlertResponse rematched = alertService.getAlert(firstId).orElseThrow();
        assertEquals(1, alertRepository.count());
        assertEquals("ACKNOWLEDGED", rematched.status());
        assertTrue(rematched.acknowledged());
        assertEquals("MATCHED", rematched.lastEvaluationState());
        assertEquals(BASE.plusMinutes(2), rematched.lastTriggeredAt());
        assertEquals(30.0, rematched.evidence().observedValue());

        alertService.resolveAlert(firstId).orElseThrow();
        occurrenceWriter.recordMatch(rule.getId(), evaluation(BASE.plusMinutes(3), 35.0, 20, 7));

        assertEquals(2, alertRepository.count());
        assertFalse(alertService.getAlerts().getFirst().alertId().equals(firstId));
    }

    @Test
    void telemetryEventIsEvaluatedOnlyAfterCommit() {
        createRule(AlertEvaluationService.TRACE_ERROR);
        TraceEntity trace = errorTrace("after-commit-trace");

        transactionTemplate.executeWithoutResult(status -> {
            traceRepository.save(trace);
            eventPublisher.publishEvent(new TelemetryChangedEvent(trace.getTraceId()));
            assertEquals(0, alertRepository.count());
        });

        assertEquals(1, alertRepository.count());
        assertEquals("after-commit-trace", alertService.getAlerts().getFirst().evidence().traceId());
    }

    @Test
    void emptyRuleRequestIsRejected() throws Exception {
        postRule("{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid alert rule"));
    }

    @Test
    void unknownRuleTypeIsRejected() throws Exception {
        postRule("""
                {"name":"Unknown","type":"UNKNOWN","severity":"WARNING"}
                """).andExpect(status().isBadRequest());
    }

    @Test
    void unknownSeverityIsRejected() throws Exception {
        postRule("""
                {"name":"Bad severity","type":"TRACE_ERROR","severity":"URGENT"}
                """).andExpect(status().isBadRequest());
    }

    @Test
    void thresholdRuleRequiresAllThresholdInputs() throws Exception {
        postRule("""
                {"name":"Incomplete","type":"ERROR_RATE_THRESHOLD","severity":"WARNING","threshold":10}
                """).andExpect(status().isBadRequest());
    }

    @Test
    void errorRateThresholdRejectsValuesAboveOneHundredPercent() throws Exception {
        postRule("""
                {"name":"Impossible error rate","type":"ERROR_RATE_THRESHOLD","severity":"WARNING",\
                 "threshold":100.01,"windowSeconds":300,"minimumSamples":20}
                """).andExpect(status().isBadRequest());
    }

    @Test
    void thresholdRulesRejectOverflowProneWindowsAndInvalidSamples() throws Exception {
        postRule("""
                {"name":"Huge window","type":"P95_LATENCY_THRESHOLD","severity":"WARNING",\
                 "threshold":1000,"windowSeconds":9223372036854775807,"minimumSamples":20}
                """).andExpect(status().isBadRequest());
        postRule("""
                {"name":"No samples","type":"P95_LATENCY_THRESHOLD","severity":"WARNING",\
                 "threshold":1000,"windowSeconds":300,"minimumSamples":0}
                """).andExpect(status().isBadRequest());
    }

    @Test
    void ruleStringsRespectPersistenceBounds() throws Exception {
        String oversizedName = "x".repeat(256);
        postRule("""
                {"name":"%s","type":"TRACE_ERROR","severity":"WARNING"}
                """.formatted(oversizedName)).andExpect(status().isBadRequest());

        String oversizedService = "s".repeat(256);
        postRule("""
                {"name":"Scoped trace error","type":"TRACE_ERROR","severity":"WARNING","serviceName":"%s"}
                """.formatted(oversizedService)).andExpect(status().isBadRequest());
    }

    @Test
    void traceErrorAcceptsNoThresholdInputs() throws Exception {
        postRule("""
                {"name":"Trace errors","type":"TRACE_ERROR","severity":"WARNING"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("TRACE_ERROR"))
                .andExpect(jsonPath("$.threshold").isEmpty())
                .andExpect(jsonPath("$.windowSeconds").isEmpty())
                .andExpect(jsonPath("$.minimumSamples").isEmpty())
                .andExpect(jsonPath("$.comparator").isEmpty());
    }

    @Test
    void unsupportedComparatorIsRejected() throws Exception {
        postRule("""
                {"name":"Bad comparator","type":"P95_LATENCY_THRESHOLD","severity":"WARNING",\
                 "threshold":1000,"windowSeconds":300,"minimumSamples":20,"comparator":"GREATER_THAN"}
                """).andExpect(status().isBadRequest());
    }

    @Test
    void validThresholdRuleIsAcceptedWithFixedInclusiveComparator() throws Exception {
        postRule("""
                {"name":"High p95 latency","type":"P95_LATENCY_THRESHOLD","severity":"WARNING",\
                 "threshold":1000,"windowSeconds":300,"minimumSamples":20}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comparator").value(AlertEvaluationService.INCLUSIVE_COMPARATOR))
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    void alertsApiReturnsStructuredEvidence() throws Exception {
        AlertRule rule = createRule(AlertEvaluationService.ERROR_RATE_THRESHOLD);
        occurrenceWriter.recordMatch(rule.getId(), evaluation(BASE, 20.0, 40, 8));

        mockMvc.perform(get("/api/v1/alerts")
                        .with(httpBasic(INVESTIGATION_USERNAME, INVESTIGATION_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ruleId").value(rule.getId()))
                .andExpect(jsonPath("$[0].evidence.metric").value("ERROR_RATE_PERCENT"))
                .andExpect(jsonPath("$[0].evidence.observedValue").value(20.0))
                .andExpect(jsonPath("$[0].evidence.sampleCount").value(40))
                .andExpect(jsonPath("$[0].evidence.errorCount").value(8));
    }

    private AlertRule createRule(String type) {
        AlertRuleRequest request = new AlertRuleRequest();
        request.setName("Test " + type);
        request.setType(type);
        request.setSeverity("WARNING");
        if (!AlertEvaluationService.TRACE_ERROR.equals(type)) {
            request.setThreshold(10.0);
            request.setWindowSeconds(300L);
            request.setMinimumSamples(10L);
        }
        alertService.createRule(request);
        return ruleRepository.findAll().getFirst();
    }

    private org.springframework.test.web.servlet.ResultActions postRule(String content) throws Exception {
        return mockMvc.perform(post("/api/v1/alerts/rules")
                .with(httpBasic(INVESTIGATION_USERNAME, INVESTIGATION_PASSWORD))
                .with(csrf())
                .contentType("application/json")
                .content(content));
    }

    private AlertEvaluation evaluation(LocalDateTime time, double observed, long samples, long errors) {
        LocalDateTime start = time.minusMinutes(5);
        AlertEvidence evidence = new AlertEvidence(
                "ERROR_RATE_PERCENT", observed, 10.0, "PERCENT", samples, errors,
                start, time, null, null, null, null, null, null, null
        );
        return new AlertEvaluation(time, start, time, "Observed error rate evidence.", null, null, null, evidence);
    }

    private TraceEntity errorTrace(String traceId) {
        TraceEntity trace = new TraceEntity(
                traceId, "checkout", "POST /checkout", BASE.minusSeconds(1), BASE,
                1000L, "ERROR", null, "POST", "/checkout"
        );
        SpanEntity root = new SpanEntity(
                "root", traceId, null, "POST /checkout", "SERVER", BASE.minusSeconds(1), BASE,
                1000L, "ERROR", null, "checkout"
        );
        root.setHttpStatusCode(503);
        trace.addSpan(root);
        trace.setRootSpanId("root");
        return trace;
    }
}
