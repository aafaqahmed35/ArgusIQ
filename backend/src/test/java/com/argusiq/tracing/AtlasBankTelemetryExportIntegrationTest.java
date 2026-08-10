package com.argusiq.tracing;

import com.argusiq.tracing.entity.MonitoredService;
import com.argusiq.tracing.entity.SpanEntity;
import com.argusiq.tracing.entity.TraceEntity;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class AtlasBankTelemetryExportIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @Autowired
    private TraceRepository traceRepository;

    @Autowired
    private MonitoredServiceRepository serviceRepository;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        traceRepository.deleteAll();
        serviceRepository.deleteAll();
    }

    @Test
    void atlasBankExportsTelemetrySuccessfullyToArgusIQ() throws Exception {
        byte[] traceIdBytes = new byte[]{(byte) 0x4b, (byte) 0xf9, (byte) 0x2f, (byte) 0x35, (byte) 0x77, (byte) 0xb3, (byte) 0x4d, (byte) 0xa6, (byte) 0xa3, (byte) 0xce, (byte) 0x92, (byte) 0x9d, (byte) 0x0e, (byte) 0x0e, (byte) 0x47, (byte) 0x36};
        byte[] rootSpanIdBytes = new byte[]{(byte) 0x00, (byte) 0xf0, (byte) 0x67, (byte) 0xaa, (byte) 0x0b, (byte) 0xa9, (byte) 0x02, (byte) 0xb7};
        byte[] childSpanIdBytes = new byte[]{(byte) 0x54, (byte) 0xa2, (byte) 0xe8, (byte) 0x60, (byte) 0x15, (byte) 0x36, (byte) 0x00, (byte) 0x00};

        Resource resource = Resource.newBuilder()
                .addAttributes(KeyValue.newBuilder()
                        .setKey("service.name")
                        .setValue(AnyValue.newBuilder().setStringValue("AtlasBank").build())
                        .build())
                .addAttributes(KeyValue.newBuilder()
                        .setKey("deployment.environment")
                        .setValue(AnyValue.newBuilder().setStringValue("production").build())
                        .build())
                .addAttributes(KeyValue.newBuilder()
                        .setKey("service.version")
                        .setValue(AnyValue.newBuilder().setStringValue("2.4.0").build())
                        .build())
                .addAttributes(KeyValue.newBuilder()
                        .setKey("telemetry.sdk.language")
                        .setValue(AnyValue.newBuilder().setStringValue("java").build())
                        .build())
                .build();

        long nowNano = System.currentTimeMillis() * 1_000_000L;

        // Parent Span: POST /api/v1/accounts/transfer
        Span rootSpan = Span.newBuilder()
                .setTraceId(ByteString.copyFrom(traceIdBytes))
                .setSpanId(ByteString.copyFrom(rootSpanIdBytes))
                .setName("POST /api/v1/accounts/transfer")
                .setKind(Span.SpanKind.SPAN_KIND_SERVER)
                .setStartTimeUnixNano(nowNano - 150_000_000L)
                .setEndTimeUnixNano(nowNano)
                .setStatus(Status.newBuilder().setCode(Status.StatusCode.STATUS_CODE_OK).build())
                .addAttributes(KeyValue.newBuilder()
                        .setKey("http.method")
                        .setValue(AnyValue.newBuilder().setStringValue("POST").build())
                        .build())
                .addAttributes(KeyValue.newBuilder()
                        .setKey("http.target")
                        .setValue(AnyValue.newBuilder().setStringValue("/api/v1/accounts/transfer").build())
                        .build())
                .build();

        // Child Span: DB query SELECT * FROM accounts WHERE id = ?
        Span childSpan = Span.newBuilder()
                .setTraceId(ByteString.copyFrom(traceIdBytes))
                .setSpanId(ByteString.copyFrom(childSpanIdBytes))
                .setParentSpanId(ByteString.copyFrom(rootSpanIdBytes))
                .setName("SELECT * FROM accounts WHERE id = ?")
                .setKind(Span.SpanKind.SPAN_KIND_CLIENT)
                .setStartTimeUnixNano(nowNano - 120_000_000L)
                .setEndTimeUnixNano(nowNano - 20_000_000L)
                .setStatus(Status.newBuilder().setCode(Status.StatusCode.STATUS_CODE_OK).build())
                .build();

        ScopeSpans scopeSpans = ScopeSpans.newBuilder()
                .addSpans(rootSpan)
                .addSpans(childSpan)
                .build();

        ResourceSpans resourceSpans = ResourceSpans.newBuilder()
                .setResource(resource)
                .addScopeSpans(scopeSpans)
                .build();

        ExportTraceServiceRequest request = ExportTraceServiceRequest.newBuilder()
                .addResourceSpans(resourceSpans)
                .build();

        // 1. Post OTLP Protobuf telemetry to /v1/traces
        mockMvc.perform(post("/v1/traces")
                        .contentType("application/x-protobuf")
                        .content(request.toByteArray()))
                .andExpect(status().isOk());

        // 2. Verify Service Discovery in PostgreSQL
        Optional<MonitoredService> serviceOpt = serviceRepository.findByServiceName("AtlasBank");
        assertTrue(serviceOpt.isPresent());
        MonitoredService service = serviceOpt.get();
        assertEquals("AtlasBank", service.getServiceName());
        assertEquals("production", service.getEnvironment());
        assertEquals("2.4.0", service.getVersion());
        assertEquals("java", service.getLanguage());
        assertEquals("ACTIVE", service.getStatus());

        // 3. Verify Trace & Span Persistence in PostgreSQL
        Optional<TraceEntity> traceOpt = traceRepository.findFirstByTraceId("4bf92f3577b34da6a3ce929d0e0e4736");
        assertTrue(traceOpt.isPresent());
        TraceEntity trace = traceOpt.get();
        assertEquals("AtlasBank", trace.getServiceName());
        assertEquals("POST /api/v1/accounts/transfer", trace.getRootSpanName());
        assertEquals("POST", trace.getHttpMethod());
        assertEquals("/api/v1/accounts/transfer", trace.getRequestUri());
        assertEquals("OK", trace.getStatusCode());
        assertEquals(2, trace.getSpans().size());

        // 4. Verify Parent-Child Relationship preserved
        SpanEntity parent = trace.getSpans().stream()
                .filter(s -> s.getParentSpanId() == null || s.getParentSpanId().isEmpty())
                .findFirst().orElseThrow();
        assertEquals("00f067aa0ba902b7", parent.getSpanId());

        SpanEntity child = trace.getSpans().stream()
                .filter(s -> "54a2e86015360000".equals(s.getSpanId()))
                .findFirst().orElseThrow();
        assertEquals("00f067aa0ba902b7", child.getParentSpanId());

        // 5. Verify Backward Compatible GET /api/v1/traces
        mockMvc.perform(get("/api/v1/traces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].httpMethod").value("POST"))
                .andExpect(jsonPath("$[0].requestUri").value("/api/v1/accounts/transfer"));
    }
}
