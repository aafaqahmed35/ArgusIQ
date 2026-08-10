package com.argusiq.tracing.dto;

public class AlertRequest {
    private String severity;
    private String status;
    private String type;
    private String title;
    private String description;
    private String relatedTrace;
    private String relatedSpan;
    private String relatedService;
    private String evidence;
    private String recommendationPlaceholder;
    private Boolean acknowledged;
    private String ownerPlaceholder;

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
    public Boolean getAcknowledged() { return acknowledged; }
    public void setAcknowledged(Boolean acknowledged) { this.acknowledged = acknowledged; }
    public String getOwnerPlaceholder() { return ownerPlaceholder; }
    public void setOwnerPlaceholder(String ownerPlaceholder) { this.ownerPlaceholder = ownerPlaceholder; }
}
