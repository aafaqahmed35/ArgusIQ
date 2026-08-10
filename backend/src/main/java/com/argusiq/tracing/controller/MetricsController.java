package com.argusiq.tracing.controller;

import com.argusiq.tracing.dto.MetricsResponse;
import com.argusiq.tracing.dto.NamedMetricDto;
import com.argusiq.tracing.service.MetricsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/metrics")
public class MetricsController {

    private final MetricsService metricsService;

    public MetricsController(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @GetMapping
    public MetricsResponse getMetrics() {
        return metricsService.getMetrics();
    }

    @GetMapping("/services")
    public List<NamedMetricDto> getServiceMetrics() {
        return metricsService.serviceMetrics();
    }

    @GetMapping("/endpoints")
    public List<NamedMetricDto> getEndpointMetrics() {
        return metricsService.endpointMetrics();
    }
}
