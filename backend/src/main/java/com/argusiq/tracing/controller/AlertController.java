package com.argusiq.tracing.controller;

import com.argusiq.tracing.dto.AlertResponse;
import com.argusiq.tracing.dto.AlertRuleRequest;
import com.argusiq.tracing.dto.AlertRuleResponse;
import com.argusiq.tracing.service.AlertService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/alerts")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @GetMapping
    public List<AlertResponse> getAlerts() {
        return alertService.getAlerts();
    }

    @GetMapping("/{id}")
    public ResponseEntity<AlertResponse> getAlert(@PathVariable Long id) {
        return alertService.getAlert(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/acknowledge")
    public ResponseEntity<AlertResponse> acknowledgeAlert(@PathVariable Long id) {
        return alertService.acknowledgeAlert(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/resolve")
    public ResponseEntity<AlertResponse> resolveAlert(@PathVariable Long id) {
        return alertService.resolveAlert(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/rules")
    public List<AlertRuleResponse> getRules() {
        return alertService.getRules();
    }

    @PostMapping("/rules")
    public AlertRuleResponse createRule(@RequestBody AlertRuleRequest request) {
        return alertService.createRule(request);
    }
}
