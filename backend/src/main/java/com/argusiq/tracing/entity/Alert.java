package com.argusiq.tracing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

@Entity
@Table(name = "alerts", indexes = {
        @Index(name = "idx_alert_status", columnList = "status"),
        @Index(name = "idx_alert_severity", columnList = "severity"),
        @Index(name = "idx_alert_service", columnList = "related_service"),
        @Index(name = "idx_alert_created", columnList = "created_time"),
        @Index(name = "idx_alert_rule_id", columnList = "rule_id"),
        @Index(name = "idx_alert_last_triggered", columnList = "last_triggered_at")
}, uniqueConstraints = @UniqueConstraint(name = "uk_alert_active_key", columnNames = "active_key"))
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long alertId;

    @Column(name = "rule_id")
    private Long ruleId;

    @Column(name = "rule_name")
    private String ruleName;

    @Column(nullable = false)
    private String severity;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_time", nullable = false)
    private LocalDateTime createdTime;

    @Column(name = "first_triggered_at", nullable = false)
    private LocalDateTime firstTriggeredAt;

    @Column(name = "last_triggered_at", nullable = false)
    private LocalDateTime lastTriggeredAt;

    @Column(name = "evaluation_time", nullable = false)
    private LocalDateTime evaluationTime;

    @Column(name = "window_start")
    private LocalDateTime windowStart;

    @Column(name = "window_end")
    private LocalDateTime windowEnd;

    @Column(name = "observed_at")
    private LocalDateTime observedAt;

    @Column(name = "resolved_time")
    private LocalDateTime resolvedTime;

    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    @Column(name = "related_trace")
    private String relatedTrace;

    @Column(name = "related_span")
    private String relatedSpan;

    @Column(name = "related_service")
    private String relatedService;

    @Column(length = 80)
    private String metric;

    @Column(name = "observed_value")
    private Double observedValue;

    @Column(name = "threshold_value")
    private Double thresholdValue;

    @Column(length = 40)
    private String unit;

    @Column(name = "sample_count")
    private Long sampleCount;

    @Column(name = "error_count")
    private Long errorCount;

    @Column(name = "operation_name")
    private String operationName;

    @Column(name = "observed_status", length = 40)
    private String observedStatus;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "last_evaluation_state", nullable = false, length = 40)
    private String lastEvaluationState;

    @Column(name = "active_key", length = 512)
    private String activeKey;

    @Column(nullable = false)
    private boolean acknowledged;

    public Alert() {
    }

    public Long getAlertId() { return alertId; }
    public Long getRuleId() { return ruleId; }
    public void setRuleId(Long ruleId) { this.ruleId = ruleId; }
    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public LocalDateTime getCreatedTime() { return createdTime; }
    public void setCreatedTime(LocalDateTime createdTime) { this.createdTime = createdTime; }
    public LocalDateTime getFirstTriggeredAt() { return firstTriggeredAt; }
    public void setFirstTriggeredAt(LocalDateTime firstTriggeredAt) { this.firstTriggeredAt = firstTriggeredAt; }
    public LocalDateTime getLastTriggeredAt() { return lastTriggeredAt; }
    public void setLastTriggeredAt(LocalDateTime lastTriggeredAt) { this.lastTriggeredAt = lastTriggeredAt; }
    public LocalDateTime getEvaluationTime() { return evaluationTime; }
    public void setEvaluationTime(LocalDateTime evaluationTime) { this.evaluationTime = evaluationTime; }
    public LocalDateTime getWindowStart() { return windowStart; }
    public void setWindowStart(LocalDateTime windowStart) { this.windowStart = windowStart; }
    public LocalDateTime getWindowEnd() { return windowEnd; }
    public void setWindowEnd(LocalDateTime windowEnd) { this.windowEnd = windowEnd; }
    public LocalDateTime getObservedAt() { return observedAt; }
    public void setObservedAt(LocalDateTime observedAt) { this.observedAt = observedAt; }
    public LocalDateTime getResolvedTime() { return resolvedTime; }
    public void setResolvedTime(LocalDateTime resolvedTime) { this.resolvedTime = resolvedTime; }
    public LocalDateTime getAcknowledgedAt() { return acknowledgedAt; }
    public void setAcknowledgedAt(LocalDateTime acknowledgedAt) { this.acknowledgedAt = acknowledgedAt; }
    public String getRelatedTrace() { return relatedTrace; }
    public void setRelatedTrace(String relatedTrace) { this.relatedTrace = relatedTrace; }
    public String getRelatedSpan() { return relatedSpan; }
    public void setRelatedSpan(String relatedSpan) { this.relatedSpan = relatedSpan; }
    public String getRelatedService() { return relatedService; }
    public void setRelatedService(String relatedService) { this.relatedService = relatedService; }
    public String getMetric() { return metric; }
    public void setMetric(String metric) { this.metric = metric; }
    public Double getObservedValue() { return observedValue; }
    public void setObservedValue(Double observedValue) { this.observedValue = observedValue; }
    public Double getThresholdValue() { return thresholdValue; }
    public void setThresholdValue(Double thresholdValue) { this.thresholdValue = thresholdValue; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public Long getSampleCount() { return sampleCount; }
    public void setSampleCount(Long sampleCount) { this.sampleCount = sampleCount; }
    public Long getErrorCount() { return errorCount; }
    public void setErrorCount(Long errorCount) { this.errorCount = errorCount; }
    public String getOperationName() { return operationName; }
    public void setOperationName(String operationName) { this.operationName = operationName; }
    public String getObservedStatus() { return observedStatus; }
    public void setObservedStatus(String observedStatus) { this.observedStatus = observedStatus; }
    public Integer getHttpStatus() { return httpStatus; }
    public void setHttpStatus(Integer httpStatus) { this.httpStatus = httpStatus; }
    public String getLastEvaluationState() { return lastEvaluationState; }
    public void setLastEvaluationState(String lastEvaluationState) { this.lastEvaluationState = lastEvaluationState; }
    public String getActiveKey() { return activeKey; }
    public void setActiveKey(String activeKey) { this.activeKey = activeKey; }
    public boolean isAcknowledged() { return acknowledged; }
    public void setAcknowledged(boolean acknowledged) { this.acknowledged = acknowledged; }
}
