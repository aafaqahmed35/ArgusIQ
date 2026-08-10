package com.argusiq.tracing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "alert_rules")
public class AlertRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private Double threshold;

    @Column(name = "window_seconds", nullable = false)
    private Long windowSeconds;

    @Column(nullable = false)
    private String comparator;

    @Column(nullable = false)
    private boolean enabled;

    public AlertRule() {
    }

    public Long getId() { return id; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Double getThreshold() { return threshold; }
    public void setThreshold(Double threshold) { this.threshold = threshold; }
    public Long getWindowSeconds() { return windowSeconds; }
    public void setWindowSeconds(Long windowSeconds) { this.windowSeconds = windowSeconds; }
    public String getComparator() { return comparator; }
    public void setComparator(String comparator) { this.comparator = comparator; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
