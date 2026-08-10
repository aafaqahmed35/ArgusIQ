package com.argusiq.tracing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "spans", indexes = {
        @Index(name = "idx_span_span_id", columnList = "span_id"),
        @Index(name = "idx_span_trace_id", columnList = "trace_id"),
        @Index(name = "idx_span_parent_span_id", columnList = "parent_span_id"),
        @Index(name = "idx_span_service_name", columnList = "service_name"),
        @Index(name = "idx_span_name", columnList = "name"),
        @Index(name = "idx_span_kind", columnList = "kind"),
        @Index(name = "idx_span_duration", columnList = "duration_ms"),
        @Index(name = "idx_span_customer", columnList = "customer_id"),
        @Index(name = "idx_span_account", columnList = "account_id"),
        @Index(name = "idx_span_loan", columnList = "loan_id"),
        @Index(name = "idx_span_transaction", columnList = "transaction_id")
})
public class SpanEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "span_id", nullable = false, length = 32)
    private String spanId;

    @Column(name = "trace_id", nullable = false, length = 64)
    private String traceId;

    @Column(name = "parent_span_id", length = 32)
    private String parentSpanId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "kind", nullable = false, length = 30)
    private String kind = "INTERNAL";

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

    @Column(name = "service_name", nullable = false)
    private String serviceName;

    @Column(name = "http_method", length = 20)
    private String httpMethod;

    @Column(name = "http_status_code")
    private Integer httpStatusCode;

    @Column(name = "customer_id")
    private String customerId;

    @Column(name = "account_id")
    private String accountId;

    @Column(name = "loan_id")
    private String loanId;

    @Column(name = "transaction_id")
    private String transactionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trace_entity_id")
    private TraceEntity trace;

    protected SpanEntity() {
    }

    public SpanEntity(String spanId, String traceId, String parentSpanId, String name, String kind, LocalDateTime startTime, LocalDateTime endTime, Long durationMs, String statusCode, String statusMessage, String serviceName) {
        this.spanId = spanId;
        this.traceId = traceId;
        this.parentSpanId = parentSpanId;
        this.name = name;
        this.kind = kind != null ? kind : "INTERNAL";
        this.startTime = startTime;
        this.endTime = endTime;
        this.durationMs = durationMs;
        this.statusCode = statusCode != null ? statusCode : "UNSET";
        this.statusMessage = statusMessage;
        this.serviceName = serviceName;
    }

    public Long getId() {
        return id;
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

    public String getServiceName() {
        return serviceName;
    }

    public TraceEntity getTrace() {
        return trace;
    }

    public void setTrace(TraceEntity trace) {
        this.trace = trace;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public void setHttpMethod(String httpMethod) {
        this.httpMethod = httpMethod;
    }

    public Integer getHttpStatusCode() {
        return httpStatusCode;
    }

    public void setHttpStatusCode(Integer httpStatusCode) {
        this.httpStatusCode = httpStatusCode;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public String getLoanId() {
        return loanId;
    }

    public void setLoanId(String loanId) {
        this.loanId = loanId;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }
}
