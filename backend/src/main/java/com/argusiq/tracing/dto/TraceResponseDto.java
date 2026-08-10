package com.argusiq.tracing.dto;

import java.time.LocalDateTime;

public class TraceResponseDto {

    private final Long id;
    private final String traceId;
    private final String serviceName;
    private final String rootSpanName;
    private final String httpMethod;
    private final String requestUri;
    private final String statusCode;
    private final String statusMessage;
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;
    private final Long durationMs;
    private final Integer spanCount;
    private final Integer errorSpanCount;
    private final Integer serviceCount;
    private final LocalDateTime timestamp;
    private final String rootSpanId;
    private final Long criticalPathDurationMs;
    private final String businessOperation;
    private final String entryEndpoint;
    private final String exitStatus;
    private final String timelineSummary;
    private final String evidenceGraphId;

    public TraceResponseDto(
            Long id,
            String traceId,
            String serviceName,
            String rootSpanName,
            String httpMethod,
            String requestUri,
            String statusCode,
            String statusMessage,
            LocalDateTime startTime,
            LocalDateTime endTime,
            Long durationMs,
            Integer spanCount,
            Integer errorSpanCount,
            Integer serviceCount,
            LocalDateTime timestamp
    ) {
        this.id = id;
        this.traceId = traceId;
        this.serviceName = serviceName != null ? serviceName : "unknown-service";
        this.rootSpanName = rootSpanName != null ? rootSpanName : (requestUri != null ? requestUri : "HTTP Request");
        this.httpMethod = httpMethod != null ? httpMethod : "OTLP";
        this.requestUri = requestUri != null ? requestUri : "/";
        this.statusCode = statusCode != null ? statusCode : "UNSET";
        this.statusMessage = statusMessage;
        this.startTime = startTime != null ? startTime : timestamp;
        this.endTime = endTime != null ? endTime : startTime;
        this.durationMs = durationMs != null ? durationMs : 0L;
        this.spanCount = spanCount != null ? spanCount : 1;
        this.errorSpanCount = errorSpanCount != null ? errorSpanCount : ("ERROR".equals(statusCode) ? 1 : 0);
        this.serviceCount = serviceCount != null ? serviceCount : 1;
        this.timestamp = timestamp != null ? timestamp : startTime;
        this.rootSpanId = null;
        this.criticalPathDurationMs = this.durationMs;
        this.businessOperation = this.rootSpanName;
        this.entryEndpoint = this.requestUri;
        this.exitStatus = this.statusCode;
        this.timelineSummary = null;
        this.evidenceGraphId = traceId != null ? "trace:" + traceId : null;
    }

    public TraceResponseDto(
            Long id,
            String traceId,
            String serviceName,
            String rootSpanName,
            String httpMethod,
            String requestUri,
            String statusCode,
            String statusMessage,
            LocalDateTime startTime,
            LocalDateTime endTime,
            Long durationMs,
            Integer spanCount,
            Integer errorSpanCount,
            Integer serviceCount,
            LocalDateTime timestamp,
            String rootSpanId,
            Long criticalPathDurationMs,
            String businessOperation,
            String entryEndpoint,
            String exitStatus,
            String timelineSummary,
            String evidenceGraphId
    ) {
        this.id = id;
        this.traceId = traceId;
        this.serviceName = serviceName != null ? serviceName : "unknown-service";
        this.rootSpanName = rootSpanName != null ? rootSpanName : (requestUri != null ? requestUri : "HTTP Request");
        this.httpMethod = httpMethod != null ? httpMethod : "OTLP";
        this.requestUri = requestUri != null ? requestUri : "/";
        this.statusCode = statusCode != null ? statusCode : "UNSET";
        this.statusMessage = statusMessage;
        this.startTime = startTime != null ? startTime : timestamp;
        this.endTime = endTime != null ? endTime : startTime;
        this.durationMs = durationMs != null ? durationMs : 0L;
        this.spanCount = spanCount != null ? spanCount : 1;
        this.errorSpanCount = errorSpanCount != null ? errorSpanCount : ("ERROR".equals(statusCode) ? 1 : 0);
        this.serviceCount = serviceCount != null ? serviceCount : 1;
        this.timestamp = timestamp != null ? timestamp : startTime;
        this.rootSpanId = rootSpanId;
        this.criticalPathDurationMs = criticalPathDurationMs != null ? criticalPathDurationMs : this.durationMs;
        this.businessOperation = businessOperation != null ? businessOperation : this.rootSpanName;
        this.entryEndpoint = entryEndpoint != null ? entryEndpoint : this.requestUri;
        this.exitStatus = exitStatus != null ? exitStatus : this.statusCode;
        this.timelineSummary = timelineSummary;
        this.evidenceGraphId = evidenceGraphId != null ? evidenceGraphId : (traceId != null ? "trace:" + traceId : null);
    }

    // Legacy constructor for backward compatibility
    public TraceResponseDto(
            Long id,
            String httpMethod,
            String requestUri,
            Long executionTimeMs,
            LocalDateTime timestamp
    ) {
        this(
                id,
                String.valueOf(id),
                "unknown-service",
                requestUri,
                httpMethod,
                requestUri,
                "OK",
                null,
                timestamp,
                timestamp,
                executionTimeMs,
                1,
                0,
                1,
                timestamp
        );
    }

    public Long getId() {
        return id;
    }

    public String getTraceId() {
        return traceId;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getRootSpanName() {
        return rootSpanName;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public String getRequestUri() {
        return requestUri;
    }

    public String getStatusCode() {
        return statusCode;
    }

    public String getStatusMessage() {
        return statusMessage;
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

    public Integer getSpanCount() {
        return spanCount;
    }

    public Integer getErrorSpanCount() {
        return errorSpanCount;
    }

    public Integer getServiceCount() {
        return serviceCount;
    }

    public Long getExecutionTimeMs() {
        return durationMs;
    }

    public LocalDateTime getTimestamp() {
        return timestamp != null ? timestamp : startTime;
    }

    public boolean isSuccess() {
        return !"ERROR".equalsIgnoreCase(statusCode);
    }

    public String getRootSpanId() {
        return rootSpanId;
    }

    public Long getCriticalPathDurationMs() {
        return criticalPathDurationMs;
    }

    public String getBusinessOperation() {
        return businessOperation;
    }

    public String getEntryEndpoint() {
        return entryEndpoint;
    }

    public String getExitStatus() {
        return exitStatus;
    }

    public String getTimelineSummary() {
        return timelineSummary;
    }

    public String getEvidenceGraphId() {
        return evidenceGraphId;
    }
}
