package com.argusiq.tracing.service;

import com.argusiq.tracing.dto.AlertEvidence;
import com.argusiq.tracing.dto.AlertResponse;
import com.argusiq.tracing.dto.AlertRuleRequest;
import com.argusiq.tracing.dto.AlertRuleResponse;
import com.argusiq.tracing.entity.Alert;
import com.argusiq.tracing.entity.AlertRule;
import com.argusiq.tracing.repository.AlertRepository;
import com.argusiq.tracing.repository.AlertRuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class AlertService {

    static final int MAX_NAME_LENGTH = 255;
    static final int MAX_SERVICE_NAME_LENGTH = 255;
    static final long MAX_WINDOW_SECONDS = 31_536_000L;
    static final long MAX_MINIMUM_SAMPLES = 1_000_000_000L;

    private final AlertRepository alertRepository;
    private final AlertRuleRepository alertRuleRepository;
    private final Clock clock;

    public AlertService(AlertRepository alertRepository, AlertRuleRepository alertRuleRepository, Clock clock) {
        this.alertRepository = alertRepository;
        this.alertRuleRepository = alertRuleRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<AlertResponse> getAlerts() {
        return alertRepository.findAllByOrderByLastTriggeredAtDescAlertIdDesc().stream().map(this::mapAlert).toList();
    }

    @Transactional(readOnly = true)
    public Optional<AlertResponse> getAlert(Long id) {
        return alertRepository.findById(id).map(this::mapAlert);
    }

    @Transactional
    public Optional<AlertResponse> acknowledgeAlert(Long id) {
        return alertRepository.findById(id).map(alert -> {
            if (!"RESOLVED".equals(alert.getStatus())) {
                alert.setAcknowledged(true);
                alert.setAcknowledgedAt(now());
                alert.setStatus("ACKNOWLEDGED");
            }
            return mapAlert(alertRepository.save(alert));
        });
    }

    @Transactional
    public Optional<AlertResponse> resolveAlert(Long id) {
        return alertRepository.findById(id).map(alert -> {
            if (!"RESOLVED".equals(alert.getStatus())) {
                alert.setStatus("RESOLVED");
                alert.setResolvedTime(now());
                alert.setActiveKey(null);
            }
            return mapAlert(alertRepository.saveAndFlush(alert));
        });
    }

    @Transactional(readOnly = true)
    public List<AlertRuleResponse> getRules() {
        return alertRuleRepository.findAllByOrderByIdAsc().stream().map(this::mapRule).toList();
    }

    @Transactional
    public AlertRuleResponse createRule(AlertRuleRequest request) {
        AlertRule rule = new AlertRule();
        applyRule(rule, request);
        LocalDateTime now = now();
        rule.setCreatedAt(now);
        rule.setUpdatedAt(now);
        return mapRule(alertRuleRepository.save(rule));
    }

    private void applyRule(AlertRule rule, AlertRuleRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request body is required");
        }
        String name = boundedText(requiredText(request.getName(), "name"), "name", MAX_NAME_LENGTH);
        String type = requiredText(request.getType(), "type").toUpperCase(Locale.ROOT);
        if (!List.of(
                AlertEvaluationService.ERROR_RATE_THRESHOLD,
                AlertEvaluationService.P95_LATENCY_THRESHOLD,
                AlertEvaluationService.TRACE_ERROR
        ).contains(type)) {
            throw new IllegalArgumentException("Unsupported alert rule type: " + type);
        }
        String severity = requiredText(request.getSeverity(), "severity").toUpperCase(Locale.ROOT);
        if (!List.of("INFO", "WARNING", "CRITICAL").contains(severity)) {
            throw new IllegalArgumentException("severity must be INFO, WARNING, or CRITICAL");
        }
        rule.setName(name);
        rule.setType(type);
        rule.setSeverity(severity);
        rule.setServiceName(boundedNullableText(request.getServiceName(), "serviceName", MAX_SERVICE_NAME_LENGTH));
        if (AlertEvaluationService.TRACE_ERROR.equals(type)) {
            rejectTraceThresholdConfiguration(request);
            rule.setThreshold(null);
            rule.setWindowSeconds(null);
            rule.setMinimumSamples(null);
            rule.setComparator(null);
        } else {
            applyThresholdConfiguration(rule, request);
        }
        rule.setEnabled(request.getEnabled() == null || request.getEnabled());
    }

    private void applyThresholdConfiguration(AlertRule rule, AlertRuleRequest request) {
        if (request.getThreshold() == null || request.getWindowSeconds() == null || request.getMinimumSamples() == null) {
            throw new IllegalArgumentException(
                    "threshold, windowSeconds, and minimumSamples are required for threshold rules"
            );
        }
        if (!Double.isFinite(request.getThreshold()) || request.getThreshold() < 0) {
            throw new IllegalArgumentException("threshold must be a finite non-negative number");
        }
        if (AlertEvaluationService.ERROR_RATE_THRESHOLD.equals(rule.getType()) && request.getThreshold() > 100) {
            throw new IllegalArgumentException("ERROR_RATE_THRESHOLD threshold must be between 0 and 100 percent");
        }
        if (request.getWindowSeconds() <= 0 || request.getWindowSeconds() > MAX_WINDOW_SECONDS) {
            throw new IllegalArgumentException("windowSeconds must be between 1 and " + MAX_WINDOW_SECONDS);
        }
        if (request.getMinimumSamples() <= 0 || request.getMinimumSamples() > MAX_MINIMUM_SAMPLES) {
            throw new IllegalArgumentException("minimumSamples must be between 1 and " + MAX_MINIMUM_SAMPLES);
        }
        if (request.getComparator() != null
                && !AlertEvaluationService.INCLUSIVE_COMPARATOR.equalsIgnoreCase(request.getComparator().trim())) {
            throw new IllegalArgumentException("Alert V1 supports only GREATER_THAN_OR_EQUAL");
        }
        rule.setThreshold(request.getThreshold());
        rule.setWindowSeconds(request.getWindowSeconds());
        rule.setMinimumSamples(request.getMinimumSamples());
        rule.setComparator(AlertEvaluationService.INCLUSIVE_COMPARATOR);
    }

    private void rejectTraceThresholdConfiguration(AlertRuleRequest request) {
        if (request.getThreshold() != null || request.getWindowSeconds() != null
                || request.getMinimumSamples() != null || request.getComparator() != null) {
            throw new IllegalArgumentException(
                    "TRACE_ERROR does not accept threshold, windowSeconds, minimumSamples, or comparator"
            );
        }
    }

    private AlertResponse mapAlert(Alert alert) {
        AlertEvidence evidence = new AlertEvidence(
                alert.getMetric(), alert.getObservedValue(), alert.getThresholdValue(), alert.getUnit(),
                alert.getSampleCount(), alert.getErrorCount(), alert.getWindowStart(), alert.getWindowEnd(),
                alert.getObservedAt(),
                alert.getRelatedTrace(), alert.getRelatedSpan(), alert.getRelatedService(), alert.getOperationName(),
                alert.getObservedStatus(), alert.getHttpStatus()
        );
        return new AlertResponse(
                alert.getAlertId(), alert.getRuleId(), alert.getRuleName(), alert.getSeverity(), alert.getStatus(),
                alert.getType(), alert.getTitle(), alert.getDescription(), alert.getFirstTriggeredAt(),
                alert.getLastTriggeredAt(), alert.getEvaluationTime(), alert.getResolvedTime(), alert.getRelatedTrace(),
                alert.getRelatedSpan(), alert.getRelatedService(), evidence, alert.isAcknowledged(),
                alert.getAcknowledgedAt(), alert.getLastEvaluationState()
        );
    }

    private AlertRuleResponse mapRule(AlertRule rule) {
        return new AlertRuleResponse(
                rule.getId(), rule.getName(), rule.getType(), rule.getSeverity(), rule.getServiceName(),
                rule.getThreshold(), rule.getWindowSeconds(), rule.getMinimumSamples(), rule.getComparator(),
                rule.isEnabled(), rule.getCreatedAt(), rule.getUpdatedAt()
        );
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private String requiredText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    private String boundedText(String value, String field, int maximumLength) {
        if (value.length() > maximumLength) {
            throw new IllegalArgumentException(field + " must be at most " + maximumLength + " characters");
        }
        return value;
    }

    private String boundedNullableText(String value, String field, int maximumLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return boundedText(value.trim(), field, maximumLength);
    }
}
