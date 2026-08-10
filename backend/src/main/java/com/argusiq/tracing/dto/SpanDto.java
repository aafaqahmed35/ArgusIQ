package com.argusiq.tracing.dto;

import java.time.LocalDateTime;

public class SpanDto {

    private final String spanId;
    private final String traceId;
    private final String parentSpanId;
    private final String name;
    private final String serviceName;
    private final String kind;
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;
    private final Long durationMs;
    private final String statusCode;
    private final String statusMessage;

    public SpanDto(
            String spanId,
            String traceId,
            String parentSpanId,
            String name,
            String serviceName,
            String kind,
            LocalDateTime startTime,
            LocalDateTime endTime,
            Long durationMs,
            String statusCode,
            String statusMessage
    ) {
        this.spanId = spanId;
        this.traceId = traceId;
        this.parentSpanId = parentSpanId;
        this.name = name;
        this.serviceName = serviceName;
        this.kind = kind;
        this.startTime = startTime;
        this.endTime = endTime;
        this.durationMs = durationMs;
        this.statusCode = statusCode;
        this.statusMessage = statusMessage;
    }

    public String getSpanId() {
        return spanId;
    }

    public String getTraceId() {
        return traceId;
    }

    public String getParentSpanId() {
        return parentSpanId;
    }

    public String getName() {
        return name;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getKind() {
        return kind;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public String getStatusCode() {
        return statusCode;
    }

    public String getStatusMessage() {
        return statusMessage;
    }
}
