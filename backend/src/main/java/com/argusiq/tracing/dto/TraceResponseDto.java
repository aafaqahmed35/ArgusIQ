package com.argusiq.tracing.dto;

import java.time.LocalDateTime;

public class TraceResponseDto {

    private final Long id;
    private final String httpMethod;
    private final String requestUri;
    private final Long executionTimeMs;
    private final LocalDateTime timestamp;

    public TraceResponseDto(
            Long id,
            String httpMethod,
            String requestUri,
            Long executionTimeMs,
            LocalDateTime timestamp
    ) {
        this.id = id;
        this.httpMethod = httpMethod;
        this.requestUri = requestUri;
        this.executionTimeMs = executionTimeMs;
        this.timestamp = timestamp;
    }

    public Long getId() {
        return id;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public String getRequestUri() {
        return requestUri;
    }

    public Long getExecutionTimeMs() {
        return executionTimeMs;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }
}
