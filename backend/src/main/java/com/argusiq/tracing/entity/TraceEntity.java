package com.argusiq.tracing.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "traces", indexes = {
        @Index(name = "idx_trace_trace_id", columnList = "trace_id"),
        @Index(name = "idx_trace_service_name", columnList = "service_name"),
        @Index(name = "idx_trace_start_time", columnList = "start_time"),
        @Index(name = "idx_trace_request_uri", columnList = "request_uri"),
        @Index(name = "idx_trace_http_method", columnList = "http_method"),
        @Index(name = "idx_trace_status_code", columnList = "status_code"),
        @Index(name = "idx_trace_business_operation", columnList = "business_operation")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_trace_trace_id", columnNames = "trace_id")
})
public class TraceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trace_id", nullable = false, length = 64, unique = true)
    private String traceId;

    @Column(name = "service_name", nullable = false)
    private String serviceName;

    @Column(name = "root_span_name", nullable = false)
    private String rootSpanName;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Column(name = "duration_ms", nullable = false)
    private Long durationMs;

    @Column(name = "status_code", nullable = false, length = 20)
    private String statusCode = "UNSET";

    @Column(name = "status_message", columnDefinition = "TEXT")
    private String statusMessage;

    @Column(name = "http_method", length = 20)
    private String httpMethod;

    @Column(name = "request_uri")
    private String requestUri;

    @Column(name = "business_operation")
    private String businessOperation;

    @Column(name = "root_span_id", length = 32)
    private String rootSpanId;

    @Column(name = "entry_endpoint")
    private String entryEndpoint;

    @Column(name = "exit_status", length = 40)
    private String exitStatus;

    @Column(name = "critical_path_duration_ms")
    private Long criticalPathDurationMs = 0L;

    @Column(name = "timeline_summary", columnDefinition = "TEXT")
    private String timelineSummary;

    @Column(name = "evidence_graph_id")
    private String evidenceGraphId;

    @OneToMany(mappedBy = "trace", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 100)
    private List<SpanEntity> spans = new ArrayList<>();

    protected TraceEntity() {
    }

    public TraceEntity(String traceId, String serviceName, String rootSpanName, LocalDateTime startTime, LocalDateTime endTime, Long durationMs, String statusCode, String statusMessage, String httpMethod, String requestUri) {
        this.traceId = traceId;
        this.serviceName = serviceName;
        this.rootSpanName = rootSpanName;
        this.startTime = startTime;
        this.endTime = endTime;
        this.durationMs = durationMs;
        this.statusCode = statusCode != null ? statusCode : "UNSET";
        this.statusMessage = statusMessage;
        this.httpMethod = httpMethod;
        this.requestUri = requestUri;
        this.businessOperation = rootSpanName;
        this.entryEndpoint = requestUri;
        this.exitStatus = statusCode != null ? statusCode : "UNSET";
        this.criticalPathDurationMs = durationMs != null ? durationMs : 0L;
        this.evidenceGraphId = traceId != null ? "trace:" + traceId : null;
    }

    public void addSpan(SpanEntity span) {
        spans.add(span);
        span.setTrace(this);
        if ("SERVER".equalsIgnoreCase(span.getKind()) && (rootSpanId == null || span.getParentSpanId() == null || span.getParentSpanId().isBlank())) {
            rootSpanId = span.getSpanId();
        }
    }

    public void clearSpans() {
        for (SpanEntity span : spans) {
            span.setTrace(null);
        }
        spans.clear();
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

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getRootSpanName() {
        return rootSpanName;
    }

    public void setRootSpanName(String rootSpanName) {
        this.rootSpanName = rootSpanName;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public String getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(String statusCode) {
        this.statusCode = statusCode;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public void setStatusMessage(String statusMessage) {
        this.statusMessage = statusMessage;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public void setHttpMethod(String httpMethod) {
        this.httpMethod = httpMethod;
    }

    public String getRequestUri() {
        return requestUri;
    }

    public void setRequestUri(String requestUri) {
        this.requestUri = requestUri;
    }

    public List<SpanEntity> getSpans() {
        return spans;
    }

    public String getBusinessOperation() {
        return businessOperation;
    }

    public void setBusinessOperation(String businessOperation) {
        this.businessOperation = businessOperation;
    }

    public String getRootSpanId() {
        return rootSpanId;
    }

    public void setRootSpanId(String rootSpanId) {
        this.rootSpanId = rootSpanId;
    }

    public String getEntryEndpoint() {
        return entryEndpoint;
    }

    public void setEntryEndpoint(String entryEndpoint) {
        this.entryEndpoint = entryEndpoint;
    }

    public String getExitStatus() {
        return exitStatus;
    }

    public void setExitStatus(String exitStatus) {
        this.exitStatus = exitStatus;
    }

    public Long getCriticalPathDurationMs() {
        return criticalPathDurationMs;
    }

    public void setCriticalPathDurationMs(Long criticalPathDurationMs) {
        this.criticalPathDurationMs = criticalPathDurationMs != null ? criticalPathDurationMs : 0L;
    }

    public String getTimelineSummary() {
        return timelineSummary;
    }

    public void setTimelineSummary(String timelineSummary) {
        this.timelineSummary = timelineSummary;
    }

    public String getEvidenceGraphId() {
        return evidenceGraphId;
    }

    public void setEvidenceGraphId(String evidenceGraphId) {
        this.evidenceGraphId = evidenceGraphId;
    }
}
