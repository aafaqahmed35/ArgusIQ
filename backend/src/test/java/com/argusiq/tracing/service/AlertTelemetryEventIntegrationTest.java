package com.argusiq.tracing.service;

import com.argusiq.AbstractArgusIqIntegrationTest;
import com.argusiq.tracing.dto.AlertResponse;
import com.argusiq.tracing.dto.AlertRuleRequest;
import com.argusiq.tracing.dto.TraceResponseDto;
import com.argusiq.tracing.entity.AlertRule;
import com.argusiq.tracing.entity.SpanEntity;
import com.argusiq.tracing.entity.TraceEntity;
import com.argusiq.tracing.event.TelemetryChangedEvent;
import com.argusiq.tracing.repository.AlertRepository;
import com.argusiq.tracing.repository.AlertRuleRepository;
import com.argusiq.tracing.repository.MonitoredServiceRepository;
import com.argusiq.tracing.repository.TraceRepository;
import com.google.protobuf.ByteString;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.resource.v1.Resource;
import io.opentelemetry.proto.trace.v1.ResourceSpans;
import io.opentelemetry.proto.trace.v1.ScopeSpans;
import io.opentelemetry.proto.trace.v1.Span;
import io.opentelemetry.proto.trace.v1.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
@RecordApplicationEvents
class AlertTelemetryEventIntegrationTest extends AbstractArgusIqIntegrationTest {

    private static final byte[] TRACE_ID_BYTES = HexFormat.of().parseHex("2030405060708090a0b0c0d0e0f00010");
    private static final String TRACE_ID = HexFormat.of().formatHex(TRACE_ID_BYTES);
    private static final long BASE_NANOS = 1_700_000_000_000_000_000L;
    private static final LocalDateTime BASE = LocalDateTime.ofEpochSecond(
            BASE_NANOS / 1_000_000_000L, 0, ZoneOffset.UTC
    );

    @Autowired
    private AlertRepository alertRepository;
    @Autowired
    private AlertRuleRepository ruleRepository;
    @Autowired
    private AlertService alertService;
    @Autowired
    private TraceRepository traceRepository;
    @Autowired
    private MonitoredServiceRepository monitoredServiceRepository;
    @Autowired
    private OtlpIngestionService ingestionService;
    @Autowired
    private TraceService traceService;
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private ApplicationEvents applicationEvents;
    @MockitoBean
    private SimpMessagingTemplate messagingTemplate;

    @BeforeEach
    void setup() {
        alertRepository.deleteAll();
        ruleRepository.deleteAll();
        traceRepository.deleteAll();
        monitoredServiceRepository.deleteAll();
        applicationEvents.clear();
        reset(messagingTemplate);
    }

    @Test
    void localMvcTracePublishesExactlyOneIdentifiedEventAndEvaluatesAfterCommit() {
        createTraceErrorRule();

        TraceEntity saved = traceService.saveHttpRequestTrace(
                "POST", "/local-order", 125L, BASE, 503
        );

        List<TelemetryChangedEvent> events = telemetryEvents();
        assertEquals(1, events.size());
        assertEquals(saved.getTraceId(), events.getFirst().traceId());
        AlertResponse occurrence = alertService.getAlerts().getFirst();
        assertEquals(saved.getTraceId(), occurrence.evidence().traceId());
        assertEquals(503, occurrence.evidence().httpStatus());
        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/traces"), any(TraceResponseDto.class));
    }

    @Test
    void successfulTraceEventCannotSelectAnOlderErrorTrace() {
        createTraceErrorRule();
        TraceEntity olderError = trace("older-error", "inventory", "reserve", "ERROR", 503);
        TraceEntity newerSuccess = trace("newer-success", "checkout", "checkout", "OK", 200);

        transactionTemplate.executeWithoutResult(status -> {
            traceRepository.save(olderError);
            traceRepository.save(newerSuccess);
            eventPublisher.publishEvent(new TelemetryChangedEvent(newerSuccess.getTraceId()));
        });

        assertEquals(0, alertRepository.count());

        transactionTemplate.executeWithoutResult(status ->
                eventPublisher.publishEvent(new TelemetryChangedEvent(olderError.getTraceId()))
        );

        AlertResponse occurrence = alertService.getAlerts().getFirst();
        assertEquals(1, alertRepository.count());
        assertEquals("older-error", occurrence.evidence().traceId());
        assertEquals("root-older-error", occurrence.evidence().spanId());
        assertEquals("inventory", occurrence.evidence().serviceName());
        assertEquals("reserve", occurrence.evidence().operationName());
        assertEquals("ERROR", occurrence.evidence().status());
        assertEquals(503, occurrence.evidence().httpStatus());
    }

    @Test
    void duplicateExporterRetryPublishesPerCommitButKeepsOneActiveOccurrence() throws Exception {
        createTraceErrorRule();
        byte[] payload = payload(
                "checkout",
                span(1, null, Span.SpanKind.SPAN_KIND_SERVER, Status.StatusCode.STATUS_CODE_ERROR,
                        0, 1_000, "POST /checkout", 503)
        );

        ingestionService.ingestProtobufTraces(payload);
        ingestionService.ingestProtobufTraces(payload);

        assertEquals(2, telemetryEvents().size());
        assertTrue(telemetryEvents().stream().allMatch(event -> TRACE_ID.equals(event.traceId())));
        assertEquals(1, alertRepository.count());
        AlertResponse occurrence = alertService.getAlerts().getFirst();
        assertEquals(TRACE_ID, occurrence.evidence().traceId());
        assertEquals(spanId(1), occurrence.evidence().spanId());
        assertEquals("checkout", occurrence.evidence().serviceName());
        assertEquals("POST /checkout", occurrence.evidence().operationName());
        assertEquals("ERROR", occurrence.evidence().status());
        assertEquals(503, occurrence.evidence().httpStatus());
    }

    @Test
    void incrementalOutOfOrderBatchesFireOnlyWhenPersistedTraceBecomesErrorAndRemainIdempotent() throws Exception {
        createTraceErrorRule();
        byte[] provisionalSuccess = payload(
                "worker",
                span(2, 1L, Span.SpanKind.SPAN_KIND_INTERNAL, Status.StatusCode.STATUS_CODE_OK,
                        100, 500, "prepare", null)
        );
        byte[] lateErrorRoot = payload(
                "gateway",
                span(1, null, Span.SpanKind.SPAN_KIND_SERVER, Status.StatusCode.STATUS_CODE_ERROR,
                        0, 1_000, "POST /orders", 503)
        );

        ingestionService.ingestProtobufTraces(provisionalSuccess);
        assertEquals(0, alertRepository.count());

        ingestionService.ingestProtobufTraces(lateErrorRoot);
        ingestionService.ingestProtobufTraces(lateErrorRoot);

        TraceEntity trace = traceRepository.findByTraceIdWithSpans(TRACE_ID).orElseThrow();
        assertEquals("ERROR", trace.getStatusCode());
        assertEquals(spanId(1), trace.getRootSpanId());
        assertEquals(2, trace.getSpans().size());
        assertEquals(3, telemetryEvents().size());
        assertEquals(1, alertRepository.count());
        AlertResponse occurrence = alertService.getAlerts().getFirst();
        assertEquals(TRACE_ID, occurrence.evidence().traceId());
        assertEquals(spanId(1), occurrence.evidence().spanId());
        assertEquals("gateway", occurrence.evidence().serviceName());
        assertEquals("POST /orders", occurrence.evidence().operationName());
        assertEquals("ERROR", occurrence.evidence().status());
        assertEquals(503, occurrence.evidence().httpStatus());
    }

    @Test
    void rolledBackTelemetryNeverProducesAnAfterCommitOccurrence() {
        createTraceErrorRule();

        transactionTemplate.executeWithoutResult(status -> {
            traceRepository.save(trace("rolled-back", "checkout", "checkout", "ERROR", 503));
            eventPublisher.publishEvent(new TelemetryChangedEvent("rolled-back"));
            status.setRollbackOnly();
        });

        assertFalse(traceRepository.findFirstByTraceId("rolled-back").isPresent());
        assertEquals(0, alertRepository.count());
    }

    @Test
    void rolledBackLocalMvcTraceNeverPublishesLiveNotification() {
        final String[] traceId = new String[1];

        transactionTemplate.executeWithoutResult(status -> {
            traceId[0] = traceService.saveHttpRequestTrace("POST", "/rolled-back-local", 25L, BASE, 503)
                    .getTraceId();
            status.setRollbackOnly();
        });

        assertFalse(traceRepository.findFirstByTraceId(traceId[0]).isPresent());
        verify(messagingTemplate, never()).convertAndSend(eq("/topic/traces"), any(TraceResponseDto.class));
    }

    @Test
    void differentTraceEventsReevaluateAggregateRuleUsingOnlyItsConfiguredServiceScope() {
        AlertRuleRequest request = new AlertRuleRequest();
        request.setName("Payment error rate");
        request.setType(AlertEvaluationService.ERROR_RATE_THRESHOLD);
        request.setSeverity("WARNING");
        request.setServiceName("payment-service");
        request.setThreshold(10.0);
        request.setWindowSeconds(300L);
        request.setMinimumSamples(1L);
        alertService.createRule(request);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        transactionTemplate.executeWithoutResult(status -> {
            traceRepository.save(traceAt(
                    "payment-error", "payment-service", "authorize", "ERROR", 500, now
            ));
            TraceEntity checkout = traceAt("checkout-trigger", "checkout-service", "checkout", "OK", 200, now);
            traceRepository.save(checkout);
            eventPublisher.publishEvent(new TelemetryChangedEvent(checkout.getTraceId()));
        });
        assertEquals(1, alertRepository.count());

        transactionTemplate.executeWithoutResult(status -> {
            TraceEntity inventory = traceAt(
                    "inventory-trigger", "inventory-service", "reserve", "OK", 200, now.plusSeconds(1)
            );
            traceRepository.save(inventory);
            eventPublisher.publishEvent(new TelemetryChangedEvent(inventory.getTraceId()));
        });

        assertEquals(1, alertRepository.count());
        AlertResponse occurrence = alertService.getAlerts().getFirst();
        assertEquals("payment-service", occurrence.evidence().serviceName());
        assertEquals(1L, occurrence.evidence().sampleCount());
        assertEquals(1L, occurrence.evidence().errorCount());
        assertEquals(100.0, occurrence.evidence().observedValue());
    }

    @Test
    void evaluatorFailureAfterCommitCannotRollBackTelemetryOrSuppressLaterRule() {
        AlertRule invalid = new AlertRule();
        invalid.setName("Invalid stored rule");
        invalid.setType("UNSUPPORTED");
        invalid.setSeverity("WARNING");
        invalid.setEnabled(true);
        invalid.setCreatedAt(BASE);
        invalid.setUpdatedAt(BASE);
        ruleRepository.saveAndFlush(invalid);
        AlertRule valid = createTraceErrorRule();

        TraceEntity saved = traceService.saveHttpRequestTrace("GET", "/committed", 10L, BASE, 503);

        assertTrue(traceRepository.findFirstByTraceId(saved.getTraceId()).isPresent());
        assertEquals(1, alertRepository.count());
        AlertResponse occurrence = alertService.getAlerts().getFirst();
        assertEquals(valid.getId(), occurrence.ruleId());
        assertEquals(saved.getTraceId(), occurrence.evidence().traceId());
        assertEquals(1, telemetryEvents().size());
    }

    private AlertRule createTraceErrorRule() {
        AlertRuleRequest request = new AlertRuleRequest();
        request.setName("Observed trace error");
        request.setType(AlertEvaluationService.TRACE_ERROR);
        request.setSeverity("WARNING");
        alertService.createRule(request);
        return ruleRepository.findAll().stream()
                .filter(rule -> AlertEvaluationService.TRACE_ERROR.equals(rule.getType()))
                .findFirst()
                .orElseThrow();
    }

    private TraceEntity trace(
            String traceId,
            String serviceName,
            String operation,
            String status,
            Integer httpStatus
    ) {
        return traceAt(traceId, serviceName, operation, status, httpStatus, BASE);
    }

    private TraceEntity traceAt(
            String traceId,
            String serviceName,
            String operation,
            String status,
            Integer httpStatus,
            LocalDateTime endTime
    ) {
        TraceEntity trace = new TraceEntity(
                traceId, serviceName, operation, endTime.minusSeconds(1), endTime,
                1_000L, status, null, "POST", "/" + operation
        );
        SpanEntity root = new SpanEntity(
                "root-" + traceId, traceId, null, operation, "SERVER",
                endTime.minusSeconds(1), endTime, 1_000L, status, null, serviceName
        );
        root.setHttpStatusCode(httpStatus);
        trace.addSpan(root);
        trace.setRootSpanId(root.getSpanId());
        return trace;
    }

    private byte[] payload(String serviceName, Span... spans) {
        Resource resource = Resource.newBuilder()
                .addAttributes(attribute("service.name", serviceName))
                .build();
        ScopeSpans scopeSpans = ScopeSpans.newBuilder().addAllSpans(List.of(spans)).build();
        ResourceSpans resourceSpans = ResourceSpans.newBuilder()
                .setResource(resource)
                .addScopeSpans(scopeSpans)
                .build();
        return ExportTraceServiceRequest.newBuilder().addResourceSpans(resourceSpans).build().toByteArray();
    }

    private Span span(
            long id,
            Long parentId,
            Span.SpanKind kind,
            Status.StatusCode status,
            long startMs,
            long endMs,
            String name,
            Integer httpStatus
    ) {
        Span.Builder builder = Span.newBuilder()
                .setTraceId(ByteString.copyFrom(TRACE_ID_BYTES))
                .setSpanId(ByteString.copyFrom(spanIdBytes(id)))
                .setName(name)
                .setKind(kind)
                .setStartTimeUnixNano(BASE_NANOS + startMs * 1_000_000L)
                .setEndTimeUnixNano(BASE_NANOS + endMs * 1_000_000L)
                .setStatus(Status.newBuilder().setCode(status).build());
        if (parentId != null) {
            builder.setParentSpanId(ByteString.copyFrom(spanIdBytes(parentId)));
        } else {
            builder.addAttributes(attribute("http.method", "POST"));
            builder.addAttributes(attribute("http.target", "/orders"));
        }
        if (httpStatus != null) {
            builder.addAttributes(attribute("http.status_code", String.valueOf(httpStatus)));
        }
        return builder.build();
    }

    private List<TelemetryChangedEvent> telemetryEvents() {
        return applicationEvents.stream(TelemetryChangedEvent.class).toList();
    }

    private KeyValue attribute(String key, String value) {
        return KeyValue.newBuilder()
                .setKey(key)
                .setValue(AnyValue.newBuilder().setStringValue(value).build())
                .build();
    }

    private byte[] spanIdBytes(long id) {
        return ByteBuffer.allocate(Long.BYTES).putLong(id).array();
    }

    private String spanId(long id) {
        return HexFormat.of().formatHex(spanIdBytes(id));
    }
}
