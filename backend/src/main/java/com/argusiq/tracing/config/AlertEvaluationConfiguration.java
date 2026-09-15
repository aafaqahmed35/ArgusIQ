package com.argusiq.tracing.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class AlertEvaluationConfiguration {

    @Bean
    public Clock alertEvaluationClock() {
        return Clock.systemUTC();
    }
}
