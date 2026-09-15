package com.argusiq.tracing.service;

import com.argusiq.tracing.dto.AlertEvidence;
import com.argusiq.tracing.entity.AlertRule;
import com.argusiq.tracing.entity.SpanEntity;
import com.argusiq.tracing.entity.TraceEntity;
import com.argusiq.tracing.event.TelemetryChangedEvent;
import com.argusiq.tracing.repository.AlertRuleRepository;
import com.argusiq.tracing.repository.TelemetryAnalyticsRepository;
import com.argusiq.tracing.repository.TelemetryAnalyticsRepository.AlertWindowAggregate;
import com.argusiq.tracing.repository.TraceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;

@Service
public class AlertEvaluationService {

    private static final Logger logger = LoggerFactory.getLogger(AlertEvaluationService.class);

    public static final String ERROR_RATE_THRESHOLD = "ERROR_RATE_THRESHOLD";
    public static final String P95_LATENCY_THRESHOLD = "P95_LATENCY_THRESHOLD";
    public static final String TRACE_ERROR = "TRACE_ERROR";
    public static final String INCLUSIVE_COMPARATOR = "GREATER_THAN_OR_EQUAL";

    private final AlertRuleRepository alertRuleRepository;
    private final TelemetryAnalyticsRepository analyticsRepository;
    private final TraceRepository traceRepository;
    private final AlertOccurrenceWriter occurrenceWriter;
    private final Clock clock;

    public AlertEvaluationService(
            AlertRuleRepository alertRuleRepository,
            TelemetryAnalyticsRepository analyticsRepository,
            TraceRepository traceRepository,
            AlertOccurrenceWriter occurrenceWriter,
            Clock clock
    ) {
        this.alertRuleRepository = alertRuleRepository;
        this.analyticsRepository = analyticsRepository;
        this.traceRepository = traceRepository;
        this.occurrenceWriter = occurrenceWriter;
        this.clock = clock;
    }

    public void evaluate(TelemetryChangedEvent event) {
        LocalDateTime evaluationTime = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        for (AlertRule rule : alertRuleRepository.findByEnabledTrueOrderByIdAsc()) {
            try {
                evaluateRule(rule, event, evaluationTime);
            } catch (RuntimeException exception) {
                logger.error(
                        "Alert rule evaluation failed after telemetry commit; continuing with remaining rules "
                                + "ruleId={} ruleName={} ruleType={} traceId={}",
                        rule.getId(), rule.getName(), rule.getType(), event != null ? event.traceId() : null,
                        exception
                );
            }
        }
    }

    void evaluateRule(AlertRule rule, TelemetryChangedEvent event, LocalDateTime evaluationTime) {
        if (!rule.isEnabled()) {
            return;
        }
        switch (rule.getType()) {
            case ERROR_RATE_THRESHOLD -> evaluateErrorRate(rule, evaluationTime);
            case P95_LATENCY_THRESHOLD -> evaluateLatency(rule, evaluationTime);
            case TRACE_ERROR -> evaluateTraceError(rule, event, evaluationTime);
            default -> throw new IllegalArgumentException("Unsupported alert rule type: " + rule.getType());
        }
    }

    private void evaluateErrorRate(AlertRule rule, LocalDateTime evaluationTime) {
        LocalDateTime windowStart = evaluationTime.minusSeconds(rule.getWindowSeconds());
        AlertWindowAggregate aggregate = analyticsRepository.alertWindowAggregate(
                windowStart, evaluationTime, rule.getServiceName()
        );
        Double observed = aggregate.errorRatePercentage();
        if (aggregate.sampleCount() < rule.getMinimumSamples() || observed == null || observed < rule.getThreshold()) {
            occurrenceWriter.recordClear(rule.getId(), evaluationTime);
            return;
        }
        AlertEvidence evidence = new AlertEvidence(
                "ERROR_RATE_PERCENT", observed, rule.getThreshold(), "PERCENT",
                aggregate.sampleCount(), aggregate.errorCount(), windowStart, evaluationTime,
                null, null, null, rule.getServiceName(), null, null, null
        );
        String summary = String.format(Locale.ROOT,
                "Observed %.1f%% error rate over %d seconds (%d/%d server-span requests); threshold %.1f%%.",
                observed, rule.getWindowSeconds(), aggregate.errorCount(), aggregate.sampleCount(), rule.getThreshold());
        occurrenceWriter.recordMatch(rule.getId(), new AlertEvaluation(
                evaluationTime, windowStart, evaluationTime, summary, null, null, rule.getServiceName(), evidence
        ));
    }

    private void evaluateLatency(AlertRule rule, LocalDateTime evaluationTime) {
        LocalDateTime windowStart = evaluationTime.minusSeconds(rule.getWindowSeconds());
        AlertWindowAggregate aggregate = analyticsRepository.alertWindowAggregate(
                windowStart, evaluationTime, rule.getServiceName()
        );
        Double observed = aggregate.p95LatencyMs();
        if (aggregate.sampleCount() < rule.getMinimumSamples() || observed == null || observed < rule.getThreshold()) {
            occurrenceWriter.recordClear(rule.getId(), evaluationTime);
            return;
        }
        AlertEvidence evidence = new AlertEvidence(
                "P95_LATENCY_MS", observed, rule.getThreshold(), "MILLISECONDS",
                aggregate.sampleCount(), null, windowStart, evaluationTime,
                null, null, null, rule.getServiceName(), null, null, null
        );
        String summary = String.format(Locale.ROOT,
                "Observed p95 latency %.1f ms across %d server-span requests over %d seconds; threshold %.1f ms.",
                observed, aggregate.sampleCount(), rule.getWindowSeconds(), rule.getThreshold());
        occurrenceWriter.recordMatch(rule.getId(), new AlertEvaluation(
                evaluationTime, windowStart, evaluationTime, summary, null, null, rule.getServiceName(), evidence
        ));
    }

    private void evaluateTraceError(AlertRule rule, TelemetryChangedEvent event, LocalDateTime evaluationTime) {
        if (event == null || event.traceId() == null) {
            return;
        }
        TraceEntity trace = traceRepository.findByTraceIdWithSpans(event.traceId()).orElse(null);
        if (trace == null || !"ERROR".equalsIgnoreCase(trace.getStatusCode())
                || !scopeMatches(rule.getServiceName(), trace.getServiceName())) {
            return;
        }
        SpanEntity root = trace.getSpans().stream()
                .filter(span -> trace.getRootSpanId() != null && trace.getRootSpanId().equals(span.getSpanId()))
                .findFirst()
                .orElse(null);
        AlertEvidence evidence = new AlertEvidence(
                "TRACE_STATUS_ERROR", 1.0, null, "OBSERVED_EVENT",
                1L, 1L, null, null, root != null ? root.getEndTime() : trace.getEndTime(),
                trace.getTraceId(), root != null ? root.getSpanId() : trace.getRootSpanId(),
                trace.getServiceName(), trace.getRootSpanName(), trace.getStatusCode(),
                root != null ? root.getHttpStatusCode() : null
        );
        String summary = "Trace " + trace.getTraceId() + " directly reported ERROR on "
                + trace.getServiceName() + " / " + trace.getRootSpanName() + ".";
        occurrenceWriter.recordMatch(rule.getId(), new AlertEvaluation(
                evaluationTime, null, null, summary, trace.getTraceId(),
                root != null ? root.getSpanId() : trace.getRootSpanId(), trace.getServiceName(), evidence
        ));
    }

    private boolean scopeMatches(String configuredService, String observedService) {
        return configuredService == null || configuredService.equals(observedService);
    }
}
