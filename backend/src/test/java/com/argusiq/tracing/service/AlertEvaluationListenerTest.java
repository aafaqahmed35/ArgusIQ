package com.argusiq.tracing.service;

import com.argusiq.tracing.event.TelemetryChangedEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class AlertEvaluationListenerTest {

    @Test
    void evaluationFailureIsIsolatedFromTelemetryPublisher() {
        AlertEvaluationService evaluationService = mock(AlertEvaluationService.class);
        TelemetryChangedEvent event = new TelemetryChangedEvent("trace-1");
        doThrow(new IllegalStateException("evaluation unavailable")).when(evaluationService).evaluate(event);

        AlertEvaluationListener listener = new AlertEvaluationListener(evaluationService);

        assertDoesNotThrow(() -> listener.onTelemetryChanged(event));
    }
}
