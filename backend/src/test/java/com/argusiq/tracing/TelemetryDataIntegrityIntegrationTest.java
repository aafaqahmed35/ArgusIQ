package com.argusiq.tracing;

import com.argusiq.AbstractArgusIqIntegrationTest;
import com.argusiq.tracing.dto.TraceDetailResponseDto;
import com.argusiq.tracing.entity.MonitoredService;
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
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest
class TelemetryDataIntegrityIntegrationTest extends AbstractArgusIqIntegrationTest {

    private static final byte[] TRACE_ID_BYTES = HexFormat.of().parseHex("abcdefabcdefabcdefabcdefabcdefab");
    private static final String TRACE_ID = HexFormat.of().formatHex(TRACE_ID_BYTES);
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
    void resourceMetadataAndObservationTimesSurviveIngestionAndReachTraceDetail() throws Exception {
        ingest(
                resource("checkout", "deployment.environment.name", "preprod", "2.4.0", "kotlin"),
                span(1, null, Span.SpanKind.SPAN_KIND_SERVER, 100, 900, "POST /checkout")
        );

        TraceEntity trace = loadTrace();
        TraceDetailResponseDto detail = traceService.getTraceByTraceId(TRACE_ID).orElseThrow();
        MonitoredService service = serviceRepository.findByServiceName("checkout").orElseThrow();
        LocalDateTime base = LocalDateTime.ofInstant(Instant.ofEpochSecond(1_700_000_000L), ZoneOffset.UTC);

        assertEquals("preprod", trace.getEnvironment());
        assertEquals("2.4.0", trace.getServiceVersion());
        assertEquals("kotlin", trace.getSdkLanguage());
        assertEquals("preprod", detail.getMetadata().getEnvironment());
        assertEquals("2.4.0", detail.getMetadata().getServiceVersion());
        assertEquals("kotlin", detail.getMetadata().getSdkLanguage());
        assertEquals(Map.of(
                "service.name", "checkout",
                "deployment.environment.name", "preprod",
                "service.version", "2.4.0",
                "telemetry.sdk.language", "kotlin"
        ), detail.getMetadata().getResourceAttributes());
        assertEquals(base.plusNanos(100_000_000L), service.getFirstSeen());
        assertEquals(base.plusNanos(900_000_000L), service.getLastSeen());
    }

    @Test
    void absentResourceMetadataRemainsHonestlyMissing() throws Exception {
        ingest(
                resource("metadata-free", null, null, null, null),
                span(1, null, Span.SpanKind.SPAN_KIND_SERVER, 0, 1_000, "GET /missing")
        );

        TraceDetailResponseDto detail = traceService.getTraceByTraceId(TRACE_ID).orElseThrow();
        MonitoredService service = serviceRepository.findByServiceName("metadata-free").orElseThrow();

        assertNull(detail.getMetadata().getEnvironment());
        assertNull(detail.getMetadata().getServiceVersion());
        assertNull(detail.getMetadata().getSdkLanguage());
        assertEquals(Map.of("service.name", "metadata-free"), detail.getMetadata().getResourceAttributes());
        assertNull(service.getEnvironment());
        assertNull(service.getVersion());
        assertNull(service.getLanguage());
        assertFalse(detail.getMetadata().getResourceAttributes().containsValue("production"));
    }

    @Test
    void incrementalMetadataEnrichmentIsIdempotentAndConflictsDoNotOscillate() throws Exception {
        Span root = span(1, null, Span.SpanKind.SPAN_KIND_SERVER, 0, 1_000, "GET /orders");
        ingest(resource("orders", "deployment.environment", "test", null, "java"), root);

        byte[] enrichment = payload(
                resource("orders", null, null, "2.1.0", null),
                span(2, 1L, Span.SpanKind.SPAN_KIND_INTERNAL, 200, 800, "validate")
        );
        ingestionService.ingestProtobufTraces(enrichment);
        ingestionService.ingestProtobufTraces(enrichment);

        Span retryWithoutHttpAttributes = root.toBuilder().clearAttributes().build();
        ingest(
                resource("orders", "deployment.environment", "production", "9.9.9", "go"),
                retryWithoutHttpAttributes
        );

        TraceEntity trace = loadTrace();
        MonitoredService service = serviceRepository.findByServiceName("orders").orElseThrow();
        LocalDateTime base = LocalDateTime.ofInstant(Instant.ofEpochSecond(1_700_000_000L), ZoneOffset.UTC);
        assertEquals(2, trace.getSpans().size());
        assertEquals("test", trace.getEnvironment());
        assertEquals("2.1.0", trace.getServiceVersion());
        assertEquals("java", trace.getSdkLanguage());
        assertEquals("POST", trace.getHttpMethod());
        assertEquals("/orders", trace.getRequestUri());
        assertEquals(base, service.getFirstSeen());
        assertEquals(base.plusSeconds(1), service.getLastSeen());
    }

    @Test
    void lateCanonicalRootRecomputesSummaryAndSupersedesProvisionalResourceMetadata() throws Exception {
        ingest(
                resource("worker", "deployment.environment", "staging", "1.0.0", "go"),
                span(2, 1L, Span.SpanKind.SPAN_KIND_INTERNAL, 100, 900, "child-before-root")
        );
        ingest(
                resource("gateway", "deployment.environment.name", "production", "3.0.0", "java"),
                span(1, null, Span.SpanKind.SPAN_KIND_SERVER, 0, 1_000, "POST /orders")
        );

        TraceEntity trace = loadTrace();
        assertEquals(spanId(1), trace.getRootSpanId());
        assertEquals("POST /orders", trace.getRootSpanName());
        assertEquals("gateway", trace.getServiceName());
        assertEquals("production", trace.getEnvironment());
        assertEquals("3.0.0", trace.getServiceVersion());
        assertEquals("java", trace.getSdkLanguage());
        assertEquals(1_000L, trace.getDurationMs());
        assertEquals("2 spans across 2 services in 1000ms", trace.getTimelineSummary());
    }

    private void ingest(Resource resource, Span... spans) throws Exception {
        ingestionService.ingestProtobufTraces(payload(resource, spans));
    }

    private byte[] payload(Resource resource, Span... spans) {
        ScopeSpans scopeSpans = ScopeSpans.newBuilder().addAllSpans(List.of(spans)).build();
        return ExportTraceServiceRequest.newBuilder()
                .addResourceSpans(ResourceSpans.newBuilder().setResource(resource).addScopeSpans(scopeSpans))
                .build()
                .toByteArray();
    }

    private Resource resource(
            String serviceName,
            String environmentKey,
            String environment,
            String version,
            String language
    ) {
        Resource.Builder resource = Resource.newBuilder().addAttributes(attribute("service.name", serviceName));
        if (environmentKey != null && environment != null) {
            resource.addAttributes(attribute(environmentKey, environment));
        }
        if (version != null) {
            resource.addAttributes(attribute("service.version", version));
        }
        if (language != null) {
            resource.addAttributes(attribute("telemetry.sdk.language", language));
        }
        return resource.build();
    }

    private Span span(long id, Long parentId, Span.SpanKind kind, long startMs, long endMs, String name) {
        Span.Builder span = Span.newBuilder()
                .setTraceId(ByteString.copyFrom(TRACE_ID_BYTES))
                .setSpanId(ByteString.copyFrom(spanIdBytes(id)))
                .setName(name)
                .setKind(kind)
                .setStartTimeUnixNano(BASE_NANOS + startMs * 1_000_000L)
                .setEndTimeUnixNano(BASE_NANOS + endMs * 1_000_000L)
                .setStatus(Status.newBuilder().setCode(Status.StatusCode.STATUS_CODE_OK));
        if (parentId != null) {
            span.setParentSpanId(ByteString.copyFrom(spanIdBytes(parentId)));
        } else {
            span.addAttributes(attribute("http.method", "POST"));
            span.addAttributes(attribute("http.target", "/orders"));
        }
        return span.build();
    }

    private TraceEntity loadTrace() {
        return traceRepository.findByTraceIdWithSpans(TRACE_ID).orElseThrow();
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
}
