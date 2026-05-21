package com.argusiq.common.dto;

import java.time.LocalDateTime;

public class HealthResponseDto {

    private final String status;
    private final String service;
    private final LocalDateTime timestamp;

    public HealthResponseDto(String status, String service, LocalDateTime timestamp) {
        this.status = status;
        this.service = service;
        this.timestamp = timestamp;
    }

    public String getStatus() {
        return status;
    }

    public String getService() {
        return service;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }
}
