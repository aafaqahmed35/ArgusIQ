package com.argusiq.tracing.service;

import com.argusiq.tracing.dto.TraceResponseDto;
import com.argusiq.tracing.entity.TraceEntity;
import com.argusiq.tracing.mapper.OtlpMapper;
import com.argusiq.tracing.repository.TraceRepository;
import com.google.protobuf.ByteString;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceResponse;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.resource.v1.Resource;
import io.opentelemetry.proto.trace.v1.ResourceSpans;
import io.opentelemetry.proto.trace.v1.ScopeSpans;
import io.opentelemetry.proto.trace.v1.Span;
import io.opentelemetry.proto.trace.v1.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OtlpIngestionServiceTest {

    private TraceRepository traceRepository;
    private ServiceDiscoveryService serviceDiscoveryService;
    private SimpMessagingTemplate messagingTemplate;
    private OtlpIngestionService otlpIngestionService;

    @BeforeEach
    void setUp() {
        traceRepository = mock(TraceRepository.class);
        serviceDiscoveryService = mock(ServiceDiscoveryService.class);
        messagingTemplate = mock(SimpMessagingTemplate.class);
        OtlpMapper otlpMapper = new OtlpMapper();

        otlpIngestionService = new OtlpIngestionService(
                traceRepository,
                serviceDiscoveryService,
                otlpMapper,
                messagingTemplate
        );
    }

    @Test
    void ingestsProtobufTraceSuccessfully() throws Exception {
        byte[] traceId = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
        byte[] spanId = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};

        Resource resource = Resource.newBuilder()
                .addAttributes(KeyValue.newBuilder()
                        .setKey("service.name")
                        .setValue(AnyValue.newBuilder().setStringValue("AtlasBankService").build())
                        .build())
                .addAttributes(KeyValue.newBuilder()
                        .setKey("deployment.environment")
                        .setValue(AnyValue.newBuilder().setStringValue("production").build())
                        .build())
                .build();

        Span span = Span.newBuilder()
                .setTraceId(ByteString.copyFrom(traceId))
                .setSpanId(ByteString.copyFrom(spanId))
                .setName("POST /api/v1/transfers")
                .setKind(Span.SpanKind.SPAN_KIND_SERVER)
                .setStartTimeUnixNano(1700000000000000000L)
                .setEndTimeUnixNano(1700000000150000000L)
                .setStatus(Status.newBuilder().setCode(Status.StatusCode.STATUS_CODE_OK).build())
                .addAttributes(KeyValue.newBuilder()
                        .setKey("http.method")
                        .setValue(AnyValue.newBuilder().setStringValue("POST").build())
                        .build())
                .addAttributes(KeyValue.newBuilder()
                        .setKey("http.target")
                        .setValue(AnyValue.newBuilder().setStringValue("/api/v1/transfers").build())
                        .build())
                .build();

        ScopeSpans scopeSpans = ScopeSpans.newBuilder()
                .addSpans(span)
                .build();

        ResourceSpans resourceSpans = ResourceSpans.newBuilder()
                .setResource(resource)
                .addScopeSpans(scopeSpans)
                .build();

        ExportTraceServiceRequest request = ExportTraceServiceRequest.newBuilder()
                .addResourceSpans(resourceSpans)
                .build();

        when(traceRepository.save(any(TraceEntity.class))).thenAnswer(i -> {
            TraceEntity t = i.getArgument(0);
            return t;
        });

        ExportTraceServiceResponse response = otlpIngestionService.ingestProtobufTraces(request.toByteArray());

        assertNotNull(response);
        verify(serviceDiscoveryService).discoverService("AtlasBankService", "production", null, null);
        verify(traceRepository).save(any(TraceEntity.class));
        verify(messagingTemplate).convertAndSend(eq("/topic/traces"), any(TraceResponseDto.class));
    }

    @Test
    void ingestsGzipCompressedProtobufTraceSuccessfully() throws Exception {
        byte[] traceId = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
        byte[] spanId = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};

        Span span = Span.newBuilder()
                .setTraceId(ByteString.copyFrom(traceId))
                .setSpanId(ByteString.copyFrom(spanId))
                .setName("GET /api/v1/health")
                .build();

        ExportTraceServiceRequest request = ExportTraceServiceRequest.newBuilder()
                .addResourceSpans(ResourceSpans.newBuilder().addScopeSpans(ScopeSpans.newBuilder().addSpans(span).build()).build())
                .build();

        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try (java.util.zip.GZIPOutputStream gzos = new java.util.zip.GZIPOutputStream(baos)) {
            gzos.write(request.toByteArray());
        }
        byte[] compressedPayload = baos.toByteArray();

        when(traceRepository.save(any(TraceEntity.class))).thenAnswer(i -> i.getArgument(0));

        ExportTraceServiceResponse response = otlpIngestionService.ingestProtobufTraces(compressedPayload);

        assertNotNull(response);
        verify(traceRepository).save(any(TraceEntity.class));
    }
}
