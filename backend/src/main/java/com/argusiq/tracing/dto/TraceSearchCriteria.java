package com.argusiq.tracing.dto;

import java.time.LocalDateTime;

public class TraceSearchCriteria {

    private String traceId;
    private String spanId;
    private String endpoint;
    private String httpMethod;
    private String status;
    private Long duration;
    private Long minDuration;
    private Long maxDuration;
    private LocalDateTime from;
    private LocalDateTime to;
    private String service;
    private String operation;
    private String businessOperation;
    private Integer statusCode;
    private Boolean rootSpan;
    private String customerId;
    private String accountId;
    private String loanId;
    private String transactionId;
    private int page = 0;
    private int size = 25;
    private String sortBy = "startTime";
    private String sortDirection = "desc";

    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public String getSpanId() { return spanId; }
    public void setSpanId(String spanId) { this.spanId = spanId; }
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getHttpMethod() { return httpMethod; }
    public void setHttpMethod(String httpMethod) { this.httpMethod = httpMethod; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getDuration() { return duration; }
    public void setDuration(Long duration) { this.duration = duration; }
    public Long getMinDuration() { return minDuration; }
    public void setMinDuration(Long minDuration) { this.minDuration = minDuration; }
    public Long getMaxDuration() { return maxDuration; }
    public void setMaxDuration(Long maxDuration) { this.maxDuration = maxDuration; }
    public LocalDateTime getFrom() { return from; }
    public void setFrom(LocalDateTime from) { this.from = from; }
    public LocalDateTime getTo() { return to; }
    public void setTo(LocalDateTime to) { this.to = to; }
    public String getService() { return service; }
    public void setService(String service) { this.service = service; }
    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }
    public String getBusinessOperation() { return businessOperation; }
    public void setBusinessOperation(String businessOperation) { this.businessOperation = businessOperation; }
    public Integer getStatusCode() { return statusCode; }
    public void setStatusCode(Integer statusCode) { this.statusCode = statusCode; }
    public Boolean getRootSpan() { return rootSpan; }
    public void setRootSpan(Boolean rootSpan) { this.rootSpan = rootSpan; }
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }
    public String getLoanId() { return loanId; }
    public void setLoanId(String loanId) { this.loanId = loanId; }
    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
    public int getPage() { return page; }
    public void setPage(int page) { this.page = Math.max(0, page); }
    public int getSize() { return size; }
    public void setSize(int size) { this.size = Math.max(1, Math.min(200, size)); }
    public String getSortBy() { return sortBy; }
    public void setSortBy(String sortBy) { this.sortBy = sortBy; }
    public String getSortDirection() { return sortDirection; }
    public void setSortDirection(String sortDirection) { this.sortDirection = sortDirection; }
}
