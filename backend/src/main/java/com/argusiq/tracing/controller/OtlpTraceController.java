package com.argusiq.tracing.controller;

import com.argusiq.tracing.service.OtlpIngestionService;
import com.google.protobuf.InvalidProtocolBufferException;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OtlpTraceController {

    private static final Logger logger = LoggerFactory.getLogger(OtlpTraceController.class);

    private final OtlpIngestionService otlpIngestionService;

    public OtlpTraceController(OtlpIngestionService otlpIngestionService) {
        this.otlpIngestionService = otlpIngestionService;
    }

    @PostMapping(
            value = {"/v1/traces", "/api/v1/otlp/v1/traces"},
            consumes = {MediaType.APPLICATION_OCTET_STREAM_VALUE, "application/x-protobuf", MediaType.ALL_VALUE},
            produces = {"application/x-protobuf", MediaType.APPLICATION_OCTET_STREAM_VALUE}
    )
    public ResponseEntity<byte[]> ingestTracesProtobuf(@RequestBody byte[] payload) {
        try {
            ExportTraceServiceResponse response = otlpIngestionService.ingestProtobufTraces(payload);
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.CONTENT_TYPE, "application/x-protobuf");
            return new ResponseEntity<>(response.toByteArray(), headers, HttpStatus.OK);
        } catch (InvalidProtocolBufferException e) {
            logger.error("Failed to parse OTLP protobuf request: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            logger.error("Unexpected error processing OTLP traces: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
