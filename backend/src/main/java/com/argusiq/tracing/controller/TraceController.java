package com.argusiq.tracing.controller;

import com.argusiq.tracing.dto.AverageResponseTimeDto;
import com.argusiq.tracing.dto.TraceCountDto;
import com.argusiq.tracing.dto.TraceResponseDto;
import com.argusiq.tracing.service.TraceLogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/traces")
public class TraceController {

    private final TraceLogService traceLogService;

    public TraceController(TraceLogService traceLogService) {
        this.traceLogService = traceLogService;
    }

    @GetMapping
    public List<TraceResponseDto> getAllTraces() {
        return traceLogService.getAllTraces();
    }

    @GetMapping("/slow")
    public List<TraceResponseDto> getSlowestTraces() {
        return traceLogService.getSlowestTraces();
    }

    @GetMapping("/analytics/average-response-time")
    public AverageResponseTimeDto getAverageResponseTime() {
        return traceLogService.getAverageResponseTime();
    }

    @GetMapping("/analytics/count")
    public TraceCountDto getTraceCount() {
        return traceLogService.getTraceCount();
    }
}
