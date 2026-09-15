package com.argusiq.tracing.dto;

public class AlertRuleRequest {
    private String name;
    private String type;
    private String severity;
    private String serviceName;
    private Double threshold;
    private Long windowSeconds;
    private Long minimumSamples;
    private String comparator;
    private Boolean enabled;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }
    public Double getThreshold() { return threshold; }
    public void setThreshold(Double threshold) { this.threshold = threshold; }
    public Long getWindowSeconds() { return windowSeconds; }
    public void setWindowSeconds(Long windowSeconds) { this.windowSeconds = windowSeconds; }
    public Long getMinimumSamples() { return minimumSamples; }
    public void setMinimumSamples(Long minimumSamples) { this.minimumSamples = minimumSamples; }
    public String getComparator() { return comparator; }
    public void setComparator(String comparator) { this.comparator = comparator; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
}
