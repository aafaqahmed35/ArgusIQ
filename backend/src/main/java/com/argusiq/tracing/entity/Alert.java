package com.argusiq.tracing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "alerts", indexes = {
        @Index(name = "idx_alert_status", columnList = "status"),
        @Index(name = "idx_alert_severity", columnList = "severity"),
        @Index(name = "idx_alert_service", columnList = "related_service"),
        @Index(name = "idx_alert_created", columnList = "created_time")
})
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long alertId;

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

    @Column(name = "resolved_time")
    private LocalDateTime resolvedTime;

    @Column(name = "related_trace")
    private String relatedTrace;

    @Column(name = "related_span")
    private String relatedSpan;

    @Column(name = "related_service")
    private String relatedService;

    @Column(columnDefinition = "TEXT")
    private String evidence;

    @Column(name = "recommendation_placeholder", columnDefinition = "TEXT")
    private String recommendationPlaceholder;

    @Column(nullable = false)
    private boolean acknowledged;

    @Column(name = "owner_placeholder")
    private String ownerPlaceholder;

    public Alert() {
    }

    public Long getAlertId() { return alertId; }
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
    public LocalDateTime getResolvedTime() { return resolvedTime; }
    public void setResolvedTime(LocalDateTime resolvedTime) { this.resolvedTime = resolvedTime; }
    public String getRelatedTrace() { return relatedTrace; }
    public void setRelatedTrace(String relatedTrace) { this.relatedTrace = relatedTrace; }
    public String getRelatedSpan() { return relatedSpan; }
    public void setRelatedSpan(String relatedSpan) { this.relatedSpan = relatedSpan; }
    public String getRelatedService() { return relatedService; }
    public void setRelatedService(String relatedService) { this.relatedService = relatedService; }
    public String getEvidence() { return evidence; }
    public void setEvidence(String evidence) { this.evidence = evidence; }
    public String getRecommendationPlaceholder() { return recommendationPlaceholder; }
    public void setRecommendationPlaceholder(String recommendationPlaceholder) { this.recommendationPlaceholder = recommendationPlaceholder; }
    public boolean isAcknowledged() { return acknowledged; }
    public void setAcknowledged(boolean acknowledged) { this.acknowledged = acknowledged; }
    public String getOwnerPlaceholder() { return ownerPlaceholder; }
    public void setOwnerPlaceholder(String ownerPlaceholder) { this.ownerPlaceholder = ownerPlaceholder; }
}
