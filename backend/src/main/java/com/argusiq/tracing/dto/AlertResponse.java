package com.argusiq.tracing.dto;

import java.time.LocalDateTime;

public class AlertResponse {
    private final Long alertId;
    private final String severity;
    private final String status;
    private final String type;
    private final String title;
    private final String description;
    private final LocalDateTime createdTime;
    private final LocalDateTime resolvedTime;
    private final String relatedTrace;
    private final String relatedSpan;
    private final String relatedService;
    private final String evidence;
    private final String recommendationPlaceholder;
    private final boolean acknowledged;
    private final String ownerPlaceholder;

    public AlertResponse(Long alertId, String severity, String status, String type, String title, String description, LocalDateTime createdTime, LocalDateTime resolvedTime, String relatedTrace, String relatedSpan, String relatedService, String evidence, String recommendationPlaceholder, boolean acknowledged, String ownerPlaceholder) {
        this.alertId = alertId;
        this.severity = severity;
        this.status = status;
        this.type = type;
        this.title = title;
        this.description = description;
        this.createdTime = createdTime;
        this.resolvedTime = resolvedTime;
        this.relatedTrace = relatedTrace;
        this.relatedSpan = relatedSpan;
        this.relatedService = relatedService;
        this.evidence = evidence;
        this.recommendationPlaceholder = recommendationPlaceholder;
        this.acknowledged = acknowledged;
        this.ownerPlaceholder = ownerPlaceholder;
    }

    public Long getAlertId() { return alertId; }
    public String getSeverity() { return severity; }
    public String getStatus() { return status; }
    public String getType() { return type; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public LocalDateTime getCreatedTime() { return createdTime; }
    public LocalDateTime getResolvedTime() { return resolvedTime; }
    public String getRelatedTrace() { return relatedTrace; }
    public String getRelatedSpan() { return relatedSpan; }
    public String getRelatedService() { return relatedService; }
    public String getEvidence() { return evidence; }
    public String getRecommendationPlaceholder() { return recommendationPlaceholder; }
    public boolean isAcknowledged() { return acknowledged; }
    public String getOwnerPlaceholder() { return ownerPlaceholder; }
}
