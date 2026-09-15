package com.argusiq.tracing.service;

import com.argusiq.tracing.entity.Alert;
import com.argusiq.tracing.entity.AlertRule;
import com.argusiq.tracing.repository.AlertRepository;
import com.argusiq.tracing.repository.AlertRuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AlertOccurrenceWriter {

    private final AlertRepository alertRepository;
    private final AlertRuleRepository alertRuleRepository;

    public AlertOccurrenceWriter(AlertRepository alertRepository, AlertRuleRepository alertRuleRepository) {
        this.alertRepository = alertRepository;
        this.alertRuleRepository = alertRuleRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordMatch(Long ruleId, AlertEvaluation evaluation) {
        AlertRule rule = alertRuleRepository.findByIdForUpdate(ruleId).orElse(null);
        if (rule == null || !rule.isEnabled()) {
            return;
        }
        String activeKey = activeKey(rule);
        Alert alert = alertRepository.findByActiveKey(activeKey).orElseGet(() -> newOccurrence(rule, evaluation, activeKey));
        applyEvaluation(alert, evaluation);
        alertRepository.saveAndFlush(alert);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordClear(Long ruleId, LocalDateTime evaluationTime) {
        AlertRule rule = alertRuleRepository.findByIdForUpdate(ruleId).orElse(null);
        if (rule == null) {
            return;
        }
        alertRepository.findByActiveKey(activeKey(rule)).ifPresent(alert -> {
            alert.setEvaluationTime(evaluationTime);
            alert.setLastEvaluationState("CLEAR");
            alertRepository.save(alert);
        });
    }

    private Alert newOccurrence(AlertRule rule, AlertEvaluation evaluation, String activeKey) {
        Alert alert = new Alert();
        alert.setRuleId(rule.getId());
        alert.setRuleName(rule.getName());
        alert.setSeverity(rule.getSeverity());
        alert.setStatus("OPEN");
        alert.setType(rule.getType());
        alert.setTitle(rule.getName());
        alert.setCreatedTime(evaluation.evaluationTime());
        alert.setFirstTriggeredAt(evaluation.evaluationTime());
        alert.setAcknowledged(false);
        alert.setLastEvaluationState("MATCHED");
        alert.setActiveKey(activeKey);
        return alert;
    }

    private void applyEvaluation(Alert alert, AlertEvaluation evaluation) {
        alert.setDescription(evaluation.summary());
        alert.setLastTriggeredAt(evaluation.evaluationTime());
        alert.setEvaluationTime(evaluation.evaluationTime());
        alert.setWindowStart(evaluation.windowStart());
        alert.setWindowEnd(evaluation.windowEnd());
        alert.setObservedAt(evaluation.evidence().observedAt());
        alert.setRelatedTrace(evaluation.traceId());
        alert.setRelatedSpan(evaluation.spanId());
        alert.setRelatedService(evaluation.serviceName());
        alert.setMetric(evaluation.evidence().metric());
        alert.setObservedValue(evaluation.evidence().observedValue());
        alert.setThresholdValue(evaluation.evidence().threshold());
        alert.setUnit(evaluation.evidence().unit());
        alert.setSampleCount(evaluation.evidence().sampleCount());
        alert.setErrorCount(evaluation.evidence().errorCount());
        alert.setOperationName(evaluation.evidence().operationName());
        alert.setObservedStatus(evaluation.evidence().status());
        alert.setHttpStatus(evaluation.evidence().httpStatus());
        alert.setLastEvaluationState("MATCHED");
    }

    static String activeKey(AlertRule rule) {
        String scope = rule.getServiceName() == null ? "*" : rule.getServiceName();
        return rule.getId() + "|" + scope;
    }
}
