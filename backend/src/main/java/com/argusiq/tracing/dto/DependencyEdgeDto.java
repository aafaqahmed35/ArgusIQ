package com.argusiq.tracing.dto;

public class DependencyEdgeDto {
    private final String source;
    private final String target;

    public DependencyEdgeDto(String source, String target) {
        this.source = source;
        this.target = target;
    }

    public String getSource() { return source; }
    public String getTarget() { return target; }
}
