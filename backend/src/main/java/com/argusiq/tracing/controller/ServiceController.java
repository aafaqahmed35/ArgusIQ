package com.argusiq.tracing.controller;

import com.argusiq.tracing.dto.DependencyGraphResponse;
import com.argusiq.tracing.dto.ServiceResponse;
import com.argusiq.tracing.service.ServicesBackendService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/services")
public class ServiceController {

    private final ServicesBackendService servicesBackendService;

    public ServiceController(ServicesBackendService servicesBackendService) {
        this.servicesBackendService = servicesBackendService;
    }

    @GetMapping
    public List<ServiceResponse> getServices() {
        return servicesBackendService.getServices();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ServiceResponse> getService(@PathVariable Long id) {
        return servicesBackendService.getService(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/dependencies")
    public DependencyGraphResponse getDependencyGraph() {
        return servicesBackendService.getDependencyGraph();
    }
}
