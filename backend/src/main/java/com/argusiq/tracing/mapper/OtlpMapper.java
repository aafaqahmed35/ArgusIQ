package com.argusiq.tracing.mapper;

import com.argusiq.tracing.criticalpath.CriticalPathResult;
import com.argusiq.tracing.dto.SpanDto;
import com.argusiq.tracing.dto.TraceDetailResponseDto;
import com.argusiq.tracing.dto.TraceMetadataDto;
import com.argusiq.tracing.dto.TraceResponseDto;
import com.argusiq.tracing.entity.SpanEntity;
import com.argusiq.tracing.entity.TraceEntity;
import com.argusiq.tracing.explanation.TraceExplanation;
import com.google.protobuf.ByteString;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.trace.v1.Span;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class OtlpMapper {

    public String bytesToHex(ByteString bytes) {
        if (bytes == null || bytes.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder(bytes.size() * 2);
        for (byte b : bytes.toByteArray()) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public LocalDateTime nanoToLocalDateTime(long nanoTime) {
        if (nanoTime <= 0) {
            throw new IllegalArgumentException("OTLP timestamp must be a positive Unix nanosecond value");
        }
        long seconds = nanoTime / 1_000_000_000L;
        int nanos = (int) (nanoTime % 1_000_000_000L);
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(seconds, nanos), ZoneOffset.UTC);
    }

    public String getAttributeValue(List<KeyValue> attributes, String key) {
        if (attributes == null || key == null) {
            return null;
        }
        for (KeyValue kv : attributes) {
            if (key.equalsIgnoreCase(kv.getKey())) {
                if (kv.getValue().hasStringValue()) {
                    return kv.getValue().getStringValue();
                } else if (kv.getValue().hasIntValue()) {
                    return String.valueOf(kv.getValue().getIntValue());
                } else if (kv.getValue().hasBoolValue()) {
                    return String.valueOf(kv.getValue().getBoolValue());
                } else if (kv.getValue().hasDoubleValue()) {
                    return String.valueOf(kv.getValue().getDoubleValue());
                }
            }
        }
        return null;
    }

    public SpanEntity mapToSpanEntity(Span otlpSpan, String serviceName) {
        String spanId = bytesToHex(otlpSpan.getSpanId());
        String traceId = bytesToHex(otlpSpan.getTraceId());
        String parentSpanId = bytesToHex(otlpSpan.getParentSpanId());

        String name = otlpSpan.getName() != null && !otlpSpan.getName().isEmpty() ? otlpSpan.getName() : "unnamed-span";
        String kind = otlpSpan.getKind().name().replace("SPAN_KIND_", "");

        long startTimeNano = otlpSpan.getStartTimeUnixNano();
        long endTimeNano = otlpSpan.getEndTimeUnixNano();
        LocalDateTime startTime = nanoToLocalDateTime(startTimeNano);
        LocalDateTime endTime = nanoToLocalDateTime(endTimeNano);
        long durationMs = (endTimeNano > startTimeNano) ? (endTimeNano - startTimeNano) / 1_000_000L : 0L;

        String statusCode = otlpSpan.getStatus().getCode().name().replace("STATUS_CODE_", "");
        String statusMessage = otlpSpan.getStatus().getMessage();

        SpanEntity spanEntity = new SpanEntity(
                spanId,
                traceId,
                parentSpanId,
                name,
                kind,
                startTime,
                endTime,
                durationMs,
                statusCode,
                statusMessage,
                serviceName
        );
        spanEntity.setHttpMethod(firstAttributeValue(otlpSpan.getAttributesList(), "http.method", "http.request.method"));
        spanEntity.setHttpStatusCode(parseInteger(firstAttributeValue(otlpSpan.getAttributesList(), "http.status_code", "http.response.status_code")));
        spanEntity.setCustomerId(firstAttributeValue(otlpSpan.getAttributesList(), "customer.id", "enduser.id", "argusiq.customer_id"));
        spanEntity.setAccountId(firstAttributeValue(otlpSpan.getAttributesList(), "account.id", "argusiq.account_id"));
        spanEntity.setLoanId(firstAttributeValue(otlpSpan.getAttributesList(), "loan.id", "argusiq.loan_id"));
        spanEntity.setTransactionId(firstAttributeValue(otlpSpan.getAttributesList(), "transaction.id", "argusiq.transaction_id"));
        return spanEntity;
    }

    public String firstAttributeValue(List<KeyValue> attributes, String... keys) {
        if (keys == null) {
            return null;
        }
        for (String key : keys) {
            String value = getAttributeValue(attributes, key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    public Integer parseInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public SpanDto mapToSpanDto(SpanEntity spanEntity) {
        if (spanEntity == null) {
            return null;
        }
        return new SpanDto(
                spanEntity.getSpanId(),
                spanEntity.getTraceId(),
                spanEntity.getParentSpanId(),
                spanEntity.getName(),
                spanEntity.getServiceName(),
                spanEntity.getKind(),
                spanEntity.getStartTime(),
                spanEntity.getEndTime(),
                spanEntity.getDurationMs(),
                spanEntity.getStatusCode(),
                spanEntity.getStatusMessage()
        );
    }

    public TraceResponseDto mapToTraceResponseDto(TraceEntity traceEntity) {
        return mapToTraceResponseDto(
                traceEntity,
                traceEntity != null ? traceEntity.getSpans() : null,
                null
        );
    }

    private TraceResponseDto mapToTraceResponseDto(
            TraceEntity traceEntity,
            List<SpanEntity> spanSnapshot,
            Long criticalPathDurationOverrideMs
    ) {
        if (traceEntity == null) {
            return null;
        }

        List<SpanEntity> spans = spanSnapshot;
        int spanCount = (spans != null && !spans.isEmpty()) ? spans.size() : 1;
        int errorSpanCount = (spans != null && !spans.isEmpty())
                ? (int) spans.stream().filter(s -> "ERROR".equalsIgnoreCase(s.getStatusCode())).count()
                : ("ERROR".equalsIgnoreCase(traceEntity.getStatusCode()) ? 1 : 0);
        int serviceCount = (spans != null && !spans.isEmpty())
                ? (int) spans.stream().map(SpanEntity::getServiceName).filter(s -> s != null && !s.isEmpty()).distinct().count()
                : 1;

        String method = traceEntity.getHttpMethod() != null ? traceEntity.getHttpMethod() : "OTLP";
        String uri = traceEntity.getRequestUri() != null ? traceEntity.getRequestUri() : traceEntity.getRootSpanName();

        return new TraceResponseDto(
                traceEntity.getId(),
                traceEntity.getTraceId(),
                traceEntity.getServiceName(),
                traceEntity.getRootSpanName(),
                method,
                uri,
                traceEntity.getStatusCode(),
                traceEntity.getStatusMessage(),
                traceEntity.getStartTime(),
                traceEntity.getEndTime(),
                traceEntity.getDurationMs(),
                spanCount,
                errorSpanCount,
                serviceCount,
                traceEntity.getStartTime(),
                traceEntity.getRootSpanId(),
                criticalPathDurationOverrideMs != null
                        ? criticalPathDurationOverrideMs
                        : traceEntity.getCriticalPathDurationMs(),
                traceEntity.getBusinessOperation(),
                traceEntity.getEntryEndpoint(),
                traceEntity.getExitStatus(),
                traceEntity.getTimelineSummary(),
                traceEntity.getEvidenceGraphId()
        );
    }

    public TraceDetailResponseDto mapToTraceDetailResponseDto(
            TraceEntity traceEntity,
            List<SpanEntity> spanSnapshot,
            CriticalPathResult criticalPath,
            TraceExplanation explanation
    ) {
        if (traceEntity == null) {
            return null;
        }

        TraceResponseDto summary = mapToTraceResponseDto(
                traceEntity,
                spanSnapshot,
                criticalPath != null ? criticalPath.totalDurationMs() : null
        );
        List<SpanDto> spanDtos = spanSnapshot != null
                ? spanSnapshot.stream().map(this::mapToSpanDto).toList()
                : List.of();

        Map<String, String> resourceAttributes = new LinkedHashMap<>();
        putObserved(resourceAttributes, "service.name", traceEntity.getServiceName(), "unknown-service");
        putObserved(resourceAttributes, "deployment.environment.name", traceEntity.getEnvironment(), null);
        putObserved(resourceAttributes, "service.version", traceEntity.getServiceVersion(), null);
        putObserved(resourceAttributes, "telemetry.sdk.language", traceEntity.getSdkLanguage(), null);

        TraceMetadataDto metadata = new TraceMetadataDto(
                traceEntity.getEnvironment(),
                traceEntity.getServiceVersion(),
                traceEntity.getSdkLanguage(),
                resourceAttributes
        );

        return new TraceDetailResponseDto(summary, spanDtos, metadata, criticalPath, explanation);
    }

    private void putObserved(Map<String, String> attributes, String key, String value, String excludedValue) {
        if (value != null && !value.isBlank() && !value.equals(excludedValue)) {
            attributes.put(key, value);
        }
    }
}
