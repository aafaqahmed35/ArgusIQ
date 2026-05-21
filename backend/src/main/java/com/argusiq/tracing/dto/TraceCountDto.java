package com.argusiq.tracing.dto;

public class TraceCountDto {

    private final Long count;

    public TraceCountDto(Long count) {
        this.count = count;
    }

    public Long getCount() {
        return count;
    }
}
