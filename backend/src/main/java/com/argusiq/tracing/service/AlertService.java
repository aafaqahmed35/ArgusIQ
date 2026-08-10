package com.argusiq.tracing.service;

import com.argusiq.tracing.dto.AlertRequest;
import com.argusiq.tracing.dto.AlertResponse;
import com.argusiq.tracing.dto.AlertRuleRequest;
import com.argusiq.tracing.dto.AlertRuleResponse;
import com.argusiq.tracing.entity.Alert;
import com.argusiq.tracing.entity.AlertRule;
import com.argusiq.tracing.repository.AlertRepository;
import com.argusiq.tracing.repository.AlertRuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@Service
public class AlertService {

    private final AlertRepository alertRepository;
    private final AlertRuleRepository alertRuleRepository;

    public AlertService(AlertRepository alertRepository, AlertRuleRepository alertRuleRepository) {
        this.alertRepository = alertRepository;
        this.alertRuleRepository = alertRuleRepository;
    }

    @Transactional(readOnly = true)
    public List<AlertResponse> getAlerts() {
        return alertRepository.findAll().stream().map(this::mapAlert).toList();
    }

    @Transactional(readOnly = true)
    public Optional<AlertResponse> getAlert(Long id) {
        return alertRepository.findById(id).map(this::mapAlert);
    }

    @Transactional
    public AlertResponse createAlert(AlertRequest request) {
        Alert alert = new Alert();
        apply(alert, request);
        alert.setCreatedTime(LocalDateTime.now(ZoneOffset.UTC));
        if (alert.getStatus() == null) {
            alert.setStatus("OPEN");
        }
        if (alert.getSeverity() == null) {
            alert.setSeverity("INFO");
        }
        if (alert.getType() == null) {
            alert.setType("MANUAL");
        }
        if (alert.getTitle() == null) {
            alert.setTitle("Manual alert");
        }
        return mapAlert(alertRepository.save(alert));
    }

    @Transactional
    public Optional<AlertResponse> updateAlert(Long id, AlertRequest request) {
        return alertRepository.findById(id).map(alert -> {
            apply(alert, request);
            if ("RESOLVED".equalsIgnoreCase(alert.getStatus()) && alert.getResolvedTime() == null) {
                alert.setResolvedTime(LocalDateTime.now(ZoneOffset.UTC));
            }
            return mapAlert(alertRepository.save(alert));
        });
    }

    @Transactional
    public boolean deleteAlert(Long id) {
        if (!alertRepository.existsById(id)) {
            return false;
        }
        alertRepository.deleteById(id);
        return true;
    }

    @Transactional(readOnly = true)
    public List<AlertRuleResponse> getRules() {
        return alertRuleRepository.findAll().stream().map(this::mapRule).toList();
    }

    @Transactional
    public AlertRuleResponse createRule(AlertRuleRequest request) {
        AlertRule rule = new AlertRule();
        apply(rule, request);
        return mapRule(alertRuleRepository.save(rule));
    }

    private void apply(Alert alert, AlertRequest request) {
        if (request.getSeverity() != null) alert.setSeverity(request.getSeverity());
        if (request.getStatus() != null) alert.setStatus(request.getStatus());
        if (request.getType() != null) alert.setType(request.getType());
        if (request.getTitle() != null) alert.setTitle(request.getTitle());
        if (request.getDescription() != null) alert.setDescription(request.getDescription());
        if (request.getRelatedTrace() != null) alert.setRelatedTrace(request.getRelatedTrace());
        if (request.getRelatedSpan() != null) alert.setRelatedSpan(request.getRelatedSpan());
        if (request.getRelatedService() != null) alert.setRelatedService(request.getRelatedService());
        if (request.getEvidence() != null) alert.setEvidence(request.getEvidence());
        if (request.getRecommendationPlaceholder() != null) alert.setRecommendationPlaceholder(request.getRecommendationPlaceholder());
        if (request.getAcknowledged() != null) alert.setAcknowledged(request.getAcknowledged());
        if (request.getOwnerPlaceholder() != null) alert.setOwnerPlaceholder(request.getOwnerPlaceholder());
    }

    private void apply(AlertRule rule, AlertRuleRequest request) {
        rule.setType(request.getType() != null ? request.getType() : "HIGH_LATENCY");
        rule.setThreshold(request.getThreshold() != null ? request.getThreshold() : 1000.0);
        rule.setWindowSeconds(request.getWindowSeconds() != null ? request.getWindowSeconds() : 300L);
        rule.setComparator(request.getComparator() != null ? request.getComparator() : "GREATER_THAN");
        rule.setEnabled(request.getEnabled() == null || request.getEnabled());
    }

    private AlertResponse mapAlert(Alert alert) {
        return new AlertResponse(alert.getAlertId(), alert.getSeverity(), alert.getStatus(), alert.getType(), alert.getTitle(), alert.getDescription(), alert.getCreatedTime(), alert.getResolvedTime(), alert.getRelatedTrace(), alert.getRelatedSpan(), alert.getRelatedService(), alert.getEvidence(), alert.getRecommendationPlaceholder(), alert.isAcknowledged(), alert.getOwnerPlaceholder());
    }

    private AlertRuleResponse mapRule(AlertRule rule) {
        return new AlertRuleResponse(rule.getId(), rule.getType(), rule.getThreshold(), rule.getWindowSeconds(), rule.getComparator(), rule.isEnabled());
    }
}
