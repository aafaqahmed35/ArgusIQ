package com.argusiq.tracing.controller;

import com.argusiq.tracing.dto.AverageResponseTimeDto;
import com.argusiq.tracing.dto.TraceCountDto;
import com.argusiq.tracing.dto.TraceDetailResponseDto;
import com.argusiq.tracing.dto.TraceResponseDto;
import com.argusiq.tracing.service.TraceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/traces")
public class TraceController {

    private final TraceService traceService;

    public TraceController(TraceService traceService) {
        this.traceService = traceService;
    }

    @GetMapping
    public List<TraceResponseDto> getAllTraces() {
        return traceService.getAllTraces();
    }

    @GetMapping("/slow")
    public List<TraceResponseDto> getSlowestTraces() {
        return traceService.getSlowestTraces();
    }

    @GetMapping("/{traceId}")
    public ResponseEntity<TraceDetailResponseDto> getTraceByTraceId(@PathVariable("traceId") String traceId) {
        return traceService.getTraceByTraceId(traceId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/analytics/average-response-time")
    public AverageResponseTimeDto getAverageResponseTime() {
        return traceService.getAverageResponseTime();
    }

    @GetMapping("/analytics/count")
    public TraceCountDto getTraceCount() {
        return traceService.getTraceCount();
    }
}
