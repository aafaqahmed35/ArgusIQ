package com.argusiq.common.service;

import com.argusiq.common.dto.HealthResponseDto;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class HealthService {

    public HealthResponseDto getHealth() {
        return new HealthResponseDto(
                "UP",
                "ArgusIQ",
                LocalDateTime.now()
        );
    }
}
