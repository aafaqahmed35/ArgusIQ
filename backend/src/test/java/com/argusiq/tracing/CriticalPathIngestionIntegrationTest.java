package com.argusiq.tracing;

import com.argusiq.AbstractArgusIqIntegrationTest;
import com.argusiq.tracing.criticalpath.CriticalPathSpanContribution;
import com.argusiq.tracing.dto.TraceDetailResponseDto;
import com.argusiq.tracing.entity.TraceEntity;
import com.argusiq.tracing.repository.MonitoredServiceRepository;
import com.argusiq.tracing.repository.TraceRepository;
import com.argusiq.tracing.service.OtlpIngestionService;
import com.argusiq.tracing.service.TraceService;
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

import java.nio.ByteBuffer;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class CriticalPathIngestionIntegrationTest extends AbstractArgusIqIntegrationTest {

    private static final byte[] INCREMENTAL_TRACE = HexFormat.of().parseHex("11111111111111111111111111111111");
    private static final byte[] OUT_OF_ORDER_TRACE = HexFormat.of().parseHex("22222222222222222222222222222222");
    private static final byte[] ONE_SHOT_TRACE = HexFormat.of().parseHex("33333333333333333333333333333333");
    private static final long BASE_NANOS = 1_700_000_000_000_000_000L;

    @Autowired
    private OtlpIngestionService ingestionService;

    @Autowired
    private TraceRepository traceRepository;

    @Autowired
    private MonitoredServiceRepository serviceRepository;

    @Autowired
    private TraceService traceService;

    @BeforeEach
    void cleanDatabase() {
        traceRepository.deleteAll();
        serviceRepository.deleteAll();
    }

    @Test
    void incrementalIngestionRecomputesFromPersistedUnionAndRetryIsIdempotent() throws Exception {
        ingest(
                span(INCREMENTAL_TRACE, 1, null, 0, 1_000, "root"),
                span(INCREMENTAL_TRACE, 2, 1L, 50, 200, "short-early")
        );
        assertEquals(1_000, load(INCREMENTAL_TRACE).getCriticalPathDurationMs());

        byte[] secondBatch = payload(span(INCREMENTAL_TRACE, 3, 1L, 100, 700, "dominant"));
        ingestionService.ingestProtobufTraces(secondBatch);

        TraceEntity afterMerge = load(INCREMENTAL_TRACE);
        TraceDetailResponseDto detail = traceService.getTraceByTraceId(traceId(INCREMENTAL_TRACE)).orElseThrow();
        assertEquals(3, afterMerge.getSpans().size());
        assertEquals(950, afterMerge.getCriticalPathDurationMs());
        assertEquals(950, detail.getCriticalPath().totalDurationMs());
        assertEquals(detail.getSummary().getCriticalPathDurationMs(), detail.getCriticalPath().totalDurationMs());
        assertEquals(1_000, detail.getSummary().getDurationMs());
        assertEquals(List.of(spanId(1), spanId(3)), detail.getCriticalPath().spans().stream()
                .map(CriticalPathSpanContribution::spanId)
                .toList());
        assertEquals(List.of(350L, 600L), detail.getCriticalPath().spans().stream()
                .map(CriticalPathSpanContribution::contributionDurationMs)
                .toList());

        ingestionService.ingestProtobufTraces(secondBatch);

        TraceDetailResponseDto afterRetry = traceService.getTraceByTraceId(traceId(INCREMENTAL_TRACE)).orElseThrow();
        assertEquals(3, load(INCREMENTAL_TRACE).getSpans().size());
        assertEquals(detail.getCriticalPath(), afterRetry.getCriticalPath());
    }

    @Test
    void childFirstIngestionConvergesToTheSameResultAsOneShotIngestion() throws Exception {
        Span outOfOrderEarly = span(OUT_OF_ORDER_TRACE, 2, 1L, 50, 200, "short-early");
        Span outOfOrderDominant = span(OUT_OF_ORDER_TRACE, 3, 1L, 100, 700, "dominant");
        ingest(outOfOrderEarly, outOfOrderDominant);
        assertEquals("PARTIAL", traceService.getTraceByTraceId(traceId(OUT_OF_ORDER_TRACE))
                .orElseThrow().getCriticalPath().status().name());
        ingest(span(OUT_OF_ORDER_TRACE, 1, null, 0, 1_000, "root"));

        ingest(
                span(ONE_SHOT_TRACE, 1, null, 0, 1_000, "root"),
                span(ONE_SHOT_TRACE, 2, 1L, 50, 200, "short-early"),
                span(ONE_SHOT_TRACE, 3, 1L, 100, 700, "dominant")
        );

        TraceDetailResponseDto outOfOrder = traceService.getTraceByTraceId(traceId(OUT_OF_ORDER_TRACE)).orElseThrow();
        TraceDetailResponseDto oneShot = traceService.getTraceByTraceId(traceId(ONE_SHOT_TRACE)).orElseThrow();
        assertEquals(oneShot.getCriticalPath().status(), outOfOrder.getCriticalPath().status());
        assertEquals(oneShot.getCriticalPath().totalDurationMs(), outOfOrder.getCriticalPath().totalDurationMs());
        assertEquals(oneShot.getCriticalPath().spans().stream().map(CriticalPathSpanContribution::contributionDurationMs).toList(),
                outOfOrder.getCriticalPath().spans().stream().map(CriticalPathSpanContribution::contributionDurationMs).toList());
        assertEquals(950, load(OUT_OF_ORDER_TRACE).getCriticalPathDurationMs());
        assertEquals(950, load(ONE_SHOT_TRACE).getCriticalPathDurationMs());
    }

    private void ingest(Span... spans) throws Exception {
        ingestionService.ingestProtobufTraces(payload(spans));
    }

    private byte[] payload(Span... spans) {
        Resource resource = Resource.newBuilder()
                .addAttributes(attribute("service.name", "critical-path-service"))
                .build();
        ScopeSpans scopeSpans = ScopeSpans.newBuilder().addAllSpans(List.of(spans)).build();
        return ExportTraceServiceRequest.newBuilder()
                .addResourceSpans(ResourceSpans.newBuilder().setResource(resource).addScopeSpans(scopeSpans))
                .build()
                .toByteArray();
    }

    private Span span(byte[] traceId, long id, Long parentId, long startMs, long endMs, String name) {
        Span.Builder builder = Span.newBuilder()
                .setTraceId(ByteString.copyFrom(traceId))
                .setSpanId(ByteString.copyFrom(spanIdBytes(id)))
                .setName(name)
                .setKind(parentId == null ? Span.SpanKind.SPAN_KIND_SERVER : Span.SpanKind.SPAN_KIND_INTERNAL)
                .setStartTimeUnixNano(BASE_NANOS + startMs * 1_000_000L)
                .setEndTimeUnixNano(BASE_NANOS + endMs * 1_000_000L)
                .setStatus(Status.newBuilder().setCode(Status.StatusCode.STATUS_CODE_OK));
        if (parentId == null) {
            builder.addAttributes(attribute("http.method", "GET"));
            builder.addAttributes(attribute("http.target", "/critical-path"));
        } else {
            builder.setParentSpanId(ByteString.copyFrom(spanIdBytes(parentId)));
        }
        return builder.build();
    }

    private TraceEntity load(byte[] traceId) {
        return traceRepository.findByTraceIdWithSpans(traceId(traceId)).orElseThrow();
    }

    private KeyValue attribute(String key, String value) {
        return KeyValue.newBuilder()
                .setKey(key)
                .setValue(AnyValue.newBuilder().setStringValue(value))
                .build();
    }

    private byte[] spanIdBytes(long id) {
        return ByteBuffer.allocate(Long.BYTES).putLong(id).array();
    }

    private String spanId(long id) {
        return HexFormat.of().formatHex(spanIdBytes(id));
    }

    private String traceId(byte[] traceId) {
        return HexFormat.of().formatHex(traceId);
    }
}
