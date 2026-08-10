package com.argusiq.tracing.dto;

public class NamedMetricDto {

    private final String name;
    private final Double value;
    private final Long count;

    public NamedMetricDto(String name, Double value, Long count) {
        this.name = name;
        this.value = value;
        this.count = count != null ? count : 0L;
    }

    public String getName() {
        return name;
    }

    public Double getValue() {
        return value;
    }

    public Long getCount() {
        return count;
    }
}
