package com.argusiq.tracing.service;

import com.argusiq.tracing.dto.AverageResponseTimeDto;
import com.argusiq.tracing.dto.TraceCountDto;
import com.argusiq.tracing.dto.TraceDetailResponseDto;
import com.argusiq.tracing.dto.TraceResponseDto;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class TraceLogService {

    private final TraceService traceService;

    public TraceLogService(TraceService traceService) {
        this.traceService = traceService;
    }

    public void saveTrace(
            String httpMethod,
            String requestUri,
            Long executionTimeMs,
            LocalDateTime timestamp
    ) {
        traceService.saveHttpRequestTrace(httpMethod, requestUri, executionTimeMs, timestamp, null);
    }

    public List<TraceResponseDto> getAllTraces() {
        return traceService.getAllTraces();
    }

    public List<TraceResponseDto> getSlowestTraces() {
        return traceService.getSlowestTraces();
    }

    public Optional<TraceDetailResponseDto> getTraceByTraceId(String traceId) {
        return traceService.getTraceByTraceId(traceId);
    }

    public AverageResponseTimeDto getAverageResponseTime() {
        return traceService.getAverageResponseTime();
    }

    public TraceCountDto getTraceCount() {
        return traceService.getTraceCount();
    }
}
