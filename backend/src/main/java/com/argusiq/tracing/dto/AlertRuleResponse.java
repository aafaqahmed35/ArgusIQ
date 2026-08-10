package com.argusiq.tracing.dto;

public class AlertRuleResponse {
    private final Long id;
    private final String type;
    private final Double threshold;
    private final Long windowSeconds;
    private final String comparator;
    private final boolean enabled;

    public AlertRuleResponse(Long id, String type, Double threshold, Long windowSeconds, String comparator, boolean enabled) {
        this.id = id;
        this.type = type;
        this.threshold = threshold;
        this.windowSeconds = windowSeconds;
        this.comparator = comparator;
        this.enabled = enabled;
    }

    public Long getId() { return id; }
    public String getType() { return type; }
    public Double getThreshold() { return threshold; }
    public Long getWindowSeconds() { return windowSeconds; }
    public String getComparator() { return comparator; }
    public boolean isEnabled() { return enabled; }
}
