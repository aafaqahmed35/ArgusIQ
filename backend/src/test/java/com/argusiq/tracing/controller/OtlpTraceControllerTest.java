package com.argusiq.tracing.controller;

import com.argusiq.tracing.service.OtlpIngestionService;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OtlpTraceControllerTest {

    private final OtlpIngestionService otlpIngestionService = mock(OtlpIngestionService.class);
    private final OtlpTraceController controller = new OtlpTraceController(otlpIngestionService);

    @Test
    void acceptsProtobufTracesAndReturnsOk() throws Exception {
        byte[] samplePayload = new byte[]{1, 2, 3};
        ExportTraceServiceResponse response = ExportTraceServiceResponse.getDefaultInstance();

        when(otlpIngestionService.ingestProtobufTraces(any())).thenReturn(response);

        ResponseEntity<byte[]> result = controller.ingestTracesProtobuf(samplePayload);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
        assertEquals("application/x-protobuf", result.getHeaders().getFirst("Content-Type"));
        verify(otlpIngestionService).ingestProtobufTraces(samplePayload);
    }
}
