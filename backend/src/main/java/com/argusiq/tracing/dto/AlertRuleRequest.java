package com.argusiq.tracing.dto;

public class AlertRuleRequest {
    private String type;
    private Double threshold;
    private Long windowSeconds;
    private String comparator;
    private Boolean enabled;

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Double getThreshold() { return threshold; }
    public void setThreshold(Double threshold) { this.threshold = threshold; }
    public Long getWindowSeconds() { return windowSeconds; }
    public void setWindowSeconds(Long windowSeconds) { this.windowSeconds = windowSeconds; }
    public String getComparator() { return comparator; }
    public void setComparator(String comparator) { this.comparator = comparator; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
}
