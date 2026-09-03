package com.argusiq.tracing.controller;

import com.argusiq.tracing.dto.EndpointMetricDto;
import com.argusiq.tracing.dto.MetricsResponse;
import com.argusiq.tracing.service.MetricsService;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    @GetMapping("/endpoints")
    public List<EndpointMetricDto> getEndpointMetrics(
            @RequestParam(defaultValue = "traffic") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDirection,
            @RequestParam(defaultValue = "100") int limit
    ) {
        try {
            return metricsService.endpointMetrics(sortBy, sortDirection, limit);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }
}
