package com.argusiq.tracing.mapper;

import com.argusiq.tracing.dto.TraceResponseDto;
import com.argusiq.tracing.entity.SpanEntity;
import com.argusiq.tracing.entity.TraceEntity;
import com.google.protobuf.ByteString;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.trace.v1.Span;
import io.opentelemetry.proto.trace.v1.Status;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class OtlpMapperTest {

    private final OtlpMapper otlpMapper = new OtlpMapper();

    @Test
    void convertsBytesToHexCorrectly() {
        byte[] bytes = new byte[]{0x4b, (byte) 0xf9, 0x2f, 0x35};
        ByteString byteString = ByteString.copyFrom(bytes);

        String hex = otlpMapper.bytesToHex(byteString);
        assertEquals("4bf92f35", hex);
    }

    @Test
    void returnsNullForEmptyByteString() {
        assertNull(otlpMapper.bytesToHex(null));
        assertNull(otlpMapper.bytesToHex(ByteString.EMPTY));
    }

    @Test
    void extractsAttributeValues() {
        KeyValue kv = KeyValue.newBuilder()
                .setKey("service.name")
                .setValue(AnyValue.newBuilder().setStringValue("AtlasBankService").build())
                .build();

        String val = otlpMapper.getAttributeValue(List.of(kv), "service.name");
        assertEquals("AtlasBankService", val);
    }

    @Test
    void mapsOtlpSpanToSpanEntity() {
        byte[] traceIdBytes = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
        byte[] spanIdBytes = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};

        Span otlpSpan = Span.newBuilder()
                .setTraceId(ByteString.copyFrom(traceIdBytes))
                .setSpanId(ByteString.copyFrom(spanIdBytes))
                .setName("GET /api/v1/accounts")
                .setKind(Span.SpanKind.SPAN_KIND_SERVER)
                .setStartTimeUnixNano(1700000000000000000L)
                .setEndTimeUnixNano(1700000000200000000L)
                .setStatus(Status.newBuilder().setCode(Status.StatusCode.STATUS_CODE_OK).build())
                .build();

        SpanEntity spanEntity = otlpMapper.mapToSpanEntity(otlpSpan, "AtlasBankService");

        assertNotNull(spanEntity);
        assertEquals("0102030405060708", spanEntity.getSpanId());
        assertEquals("0102030405060708090a0b0c0d0e0f10", spanEntity.getTraceId());
        assertEquals("GET /api/v1/accounts", spanEntity.getName());
        assertEquals("SERVER", spanEntity.getKind());
        assertEquals(200L, spanEntity.getDurationMs());
        assertEquals("OK", spanEntity.getStatusCode());
        assertEquals("AtlasBankService", spanEntity.getServiceName());
    }

    @Test
    void mapsTraceEntityToTraceResponseDto() {
        TraceEntity traceEntity = new TraceEntity(
                "4bf92f3577b34da6a3ce929d0e0e4736",
                "AtlasBankService",
                "GET /api/v1/transfers",
                LocalDateTime.now(),
                LocalDateTime.now().plusSeconds(1),
                1000L,
                "OK",
                null,
                "GET",
                "/api/v1/transfers"
        );

        TraceResponseDto dto = otlpMapper.mapToTraceResponseDto(traceEntity);

        assertNotNull(dto);
        assertEquals("GET", dto.getHttpMethod());
        assertEquals("/api/v1/transfers", dto.getRequestUri());
        assertEquals(1000L, dto.getExecutionTimeMs());
    }
}
