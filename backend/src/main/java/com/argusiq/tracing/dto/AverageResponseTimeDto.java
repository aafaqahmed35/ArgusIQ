package com.argusiq.tracing.dto;

public class AverageResponseTimeDto {

    private final Double averageResponseTimeMs;

    public AverageResponseTimeDto(Double averageResponseTimeMs) {
        this.averageResponseTimeMs = averageResponseTimeMs;
    }

    public Double getAverageResponseTimeMs() {
        return averageResponseTimeMs;
    }
}
