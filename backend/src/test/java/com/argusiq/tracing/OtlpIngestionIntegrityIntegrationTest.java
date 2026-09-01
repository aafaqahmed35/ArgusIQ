package com.argusiq.tracing;

import com.argusiq.AbstractArgusIqIntegrationTest;
import com.argusiq.tracing.entity.SpanEntity;
import com.argusiq.tracing.entity.TraceEntity;
import com.argusiq.tracing.repository.MonitoredServiceRepository;
import com.argusiq.tracing.repository.SpanRepository;
import com.argusiq.tracing.repository.TraceRepository;
import com.argusiq.tracing.service.OtlpIngestionService;
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
import org.springframework.dao.DataIntegrityViolationException;

import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class OtlpIngestionIntegrityIntegrationTest extends AbstractArgusIqIntegrationTest {

    private static final byte[] TRACE_ID_BYTES = HexFormat.of().parseHex("102030405060708090a0b0c0d0e0f000");
    private static final String TRACE_ID = HexFormat.of().formatHex(TRACE_ID_BYTES);
    private static final long BASE_NANOS = 1_700_000_000_000_000_000L;

    @Autowired
    private OtlpIngestionService ingestionService;

    @Autowired
    private TraceRepository traceRepository;

    @Autowired
    private SpanRepository spanRepository;

    @Autowired
    private MonitoredServiceRepository serviceRepository;

    @BeforeEach
    void cleanDatabase() {
        traceRepository.deleteAll();
        serviceRepository.deleteAll();
    }

    @Test
    void duplicateSpanInOnePayloadProducesOneCanonicalSpan() throws Exception {
        Span root = span(1, null, Span.SpanKind.SPAN_KIND_SERVER, 0, 1_000, "root");

        ingest(root, root);

        assertEquals(1, spanRepository.countByTraceId(TRACE_ID));
        assertEquals(1, loadTrace().getSpans().size());
    }

    @Test
    void multipleBatchesProduceTheUnionOfSpans() throws Exception {
        ingest(
                span(1, null, Span.SpanKind.SPAN_KIND_SERVER, 0, 1_000, "root"),
                span(2, 1L, Span.SpanKind.SPAN_KIND_INTERNAL, 100, 300, "validate"),
                span(3, 1L, Span.SpanKind.SPAN_KIND_CLIENT, 350, 600, "database")
        );
        ingest(
                span(4, 1L, Span.SpanKind.SPAN_KIND_CLIENT, 620, 800, "cache"),
                span(5, 1L, Span.SpanKind.SPAN_KIND_INTERNAL, 820, 900, "serialize")
        );

        TraceEntity trace = loadTrace();
        assertEquals(5, trace.getSpans().size());
        assertEquals(1_000L, trace.getDurationMs());
    }

    @Test
    void lateParentSpanIsAddedWithoutLosingExistingChildren() throws Exception {
        ingest(
                span(1, null, Span.SpanKind.SPAN_KIND_SERVER, 0, 1_000, "root"),
                span(2, 1L, Span.SpanKind.SPAN_KIND_INTERNAL, 100, 900, "orchestrate"),
                span(4, 3L, Span.SpanKind.SPAN_KIND_CLIENT, 300, 500, "late-child"),
                span(5, 2L, Span.SpanKind.SPAN_KIND_INTERNAL, 600, 700, "existing-child")
        );
        ingest(span(3, 2L, Span.SpanKind.SPAN_KIND_INTERNAL, 250, 550, "late-parent"));

        TraceEntity trace = loadTrace();
        assertEquals(5, trace.getSpans().size());
        SpanEntity child = trace.getSpans().stream()
                .filter(item -> spanId(4).equals(item.getSpanId()))
                .findFirst()
                .orElseThrow();
        assertEquals(spanId(3), child.getParentSpanId());
    }

    @Test
    void retryingTheSameBatchDoesNotCreateDuplicates() throws Exception {
        Span root = span(1, null, Span.SpanKind.SPAN_KIND_SERVER, 0, 1_000, "root");
        Span child = span(2, 1L, Span.SpanKind.SPAN_KIND_CLIENT, 100, 900, "dependency");

        ingest(root, child);
        ingest(root, child);

        assertEquals(1, traceRepository.count());
        assertEquals(2, spanRepository.countByTraceId(TRACE_ID));
    }

    @Test
    void concurrentBatchesForOneTracePreserveBothBatches() throws Exception {
        byte[] firstPayload = payload(
                span(1, null, Span.SpanKind.SPAN_KIND_SERVER, 0, 1_000, "root"),
                span(2, 1L, Span.SpanKind.SPAN_KIND_INTERNAL, 100, 300, "first-batch")
        );
        byte[] secondPayload = payload(
                span(3, 1L, Span.SpanKind.SPAN_KIND_CLIENT, 350, 700, "second-batch"),
                span(4, 3L, Span.SpanKind.SPAN_KIND_INTERNAL, 400, 600, "second-child")
        );
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> first = executor.submit(() -> ingestAfterSignal(firstPayload, ready, start));
            Future<?> second = executor.submit(() -> ingestAfterSignal(secondPayload, ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        TraceEntity trace = loadTrace();
        assertEquals(4, trace.getSpans().size());
        assertEquals(spanId(1), trace.getRootSpanId());
    }

    @Test
    void ingestionPreservesSpansAlreadyStoredForTheTrace() throws Exception {
        LocalDateTime start = LocalDateTime.ofEpochSecond(BASE_NANOS / 1_000_000_000L, 0, ZoneOffset.UTC);
        TraceEntity existing = new TraceEntity(
                TRACE_ID,
                "integrity-service",
                "root",
                start,
                start.plusSeconds(1),
                1_000L,
                "OK",
                null,
                "GET",
                "/integrity"
        );
        existing.setRootSpanId(spanId(1));
        existing.addSpan(entitySpan(1, null, start, start.plusSeconds(1), "root", "SERVER"));
        existing.addSpan(entitySpan(2, 1L, start.plusNanos(100_000_000L), start.plusNanos(300_000_000L), "stored", "INTERNAL"));
        traceRepository.saveAndFlush(existing);

        ingest(span(3, 1L, Span.SpanKind.SPAN_KIND_CLIENT, 400, 800, "new"));

        List<String> spanIds = loadTrace().getSpans().stream().map(SpanEntity::getSpanId).toList();
        assertEquals(3, spanIds.size());
        assertTrue(spanIds.containsAll(List.of(spanId(1), spanId(2), spanId(3))));
    }

    @Test
    void databaseRejectsDuplicateTraceAndSpanIdentity() {
        LocalDateTime start = LocalDateTime.now(ZoneOffset.UTC);
        TraceEntity trace = new TraceEntity(TRACE_ID, "integrity-service", "root", start, start.plusSeconds(1), 1_000L, "OK", null, "GET", "/integrity");
        trace.addSpan(entitySpan(1, null, start, start.plusSeconds(1), "root", "SERVER"));
        trace.addSpan(entitySpan(1, null, start, start.plusSeconds(1), "duplicate", "SERVER"));

        assertThrows(DataIntegrityViolationException.class, () -> traceRepository.saveAndFlush(trace));
    }

    private void ingest(Span... spans) throws Exception {
        ingestionService.ingestProtobufTraces(payload(spans));
    }

    private byte[] payload(Span... spans) {
        Resource resource = Resource.newBuilder()
                .addAttributes(attribute("service.name", "integrity-service"))
                .addAttributes(attribute("deployment.environment", "test"))
                .build();
        ScopeSpans scopeSpans = ScopeSpans.newBuilder().addAllSpans(List.of(spans)).build();
        ResourceSpans resourceSpans = ResourceSpans.newBuilder()
                .setResource(resource)
                .addScopeSpans(scopeSpans)
                .build();
        return ExportTraceServiceRequest.newBuilder().addResourceSpans(resourceSpans).build().toByteArray();
    }

    private Span span(long id, Long parentId, Span.SpanKind kind, long startMs, long endMs, String name) {
        Span.Builder builder = Span.newBuilder()
                .setTraceId(ByteString.copyFrom(TRACE_ID_BYTES))
                .setSpanId(ByteString.copyFrom(spanIdBytes(id)))
                .setName(name)
                .setKind(kind)
                .setStartTimeUnixNano(BASE_NANOS + startMs * 1_000_000L)
                .setEndTimeUnixNano(BASE_NANOS + endMs * 1_000_000L)
                .setStatus(Status.newBuilder().setCode(Status.StatusCode.STATUS_CODE_OK).build());
        if (parentId != null) {
            builder.setParentSpanId(ByteString.copyFrom(spanIdBytes(parentId)));
        }
        if (parentId == null) {
            builder.addAttributes(attribute("http.method", "GET"));
            builder.addAttributes(attribute("http.target", "/integrity"));
        }
        return builder.build();
    }

    private SpanEntity entitySpan(long id, Long parentId, LocalDateTime start, LocalDateTime end, String name, String kind) {
        return new SpanEntity(
                spanId(id),
                TRACE_ID,
                parentId != null ? spanId(parentId) : null,
                name,
                kind,
                start,
                end,
                Math.max(0L, java.time.Duration.between(start, end).toMillis()),
                "OK",
                null,
                "integrity-service"
        );
    }

    private TraceEntity loadTrace() {
        TraceEntity trace = traceRepository.findByTraceIdWithSpans(TRACE_ID).orElseThrow();
        assertNotNull(trace.getId());
        return trace;
    }

    private void ingestAfterSignal(byte[] payload, CountDownLatch ready, CountDownLatch start) {
        try {
            ready.countDown();
            start.await(5, TimeUnit.SECONDS);
            ingestionService.ingestProtobufTraces(payload);
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
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
