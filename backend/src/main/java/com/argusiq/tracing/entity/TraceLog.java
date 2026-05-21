package com.argusiq.tracing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "trace_logs")
public class TraceLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "http_method", nullable = false, length = 20)
    private String httpMethod;

    @Column(name = "request_uri", nullable = false)
    private String requestUri;

    @Column(name = "execution_time_ms", nullable = false)
    private Long executionTimeMs;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    protected TraceLog() {
    }

    public TraceLog(String httpMethod, String requestUri, Long executionTimeMs, LocalDateTime timestamp) {
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
