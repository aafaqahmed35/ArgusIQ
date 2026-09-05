package com.argusiq.tracing.explanation;

/**
 * Concrete telemetry fields supporting one finding. Null fields are not
 * applicable to that finding; no value is inferred from an operation name.
 */
public record TraceFindingEvidence(
        String spanId,
        String parentSpanId,
        String serviceName,
        String operationName,
        String relatedSpanId,
        String relatedServiceName,
        String relatedOperationName,
        Long durationMs,
        Long selfTimeMs,
        Long contributionDurationMs,
        Integer contributionPercentage,
        Long overlapDurationMs,
        String statusCode,
        Integer httpStatusCode,
        String relationship
) {
}
