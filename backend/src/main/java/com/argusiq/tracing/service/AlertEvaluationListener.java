package com.argusiq.tracing.service;

import com.argusiq.tracing.event.TelemetryChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class AlertEvaluationListener {

    private static final Logger logger = LoggerFactory.getLogger(AlertEvaluationListener.class);

    private final AlertEvaluationService evaluationService;

    public AlertEvaluationListener(AlertEvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTelemetryChanged(TelemetryChangedEvent event) {
        try {
            evaluationService.evaluate(event);
        } catch (RuntimeException exception) {
            logger.error("Alert evaluation failed after telemetry commit for traceId={}", event.traceId(), exception);
        }
    }
}
