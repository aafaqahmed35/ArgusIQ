package com.argusiq.tracing.service;

import com.argusiq.tracing.dto.TraceResponseDto;
import com.argusiq.tracing.entity.SpanEntity;
import com.argusiq.tracing.entity.TraceEntity;
import com.argusiq.tracing.mapper.OtlpMapper;
import com.argusiq.tracing.repository.TraceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class OtlpTraceMergeService {

    private final TraceRepository traceRepository;
    private final OtlpMapper otlpMapper;

    public OtlpTraceMergeService(TraceRepository traceRepository, OtlpMapper otlpMapper) {
        this.traceRepository = traceRepository;
        this.otlpMapper = otlpMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TraceResponseDto mergeTrace(TraceEntity incomingTrace) {
        TraceEntity storedTrace = traceRepository.findByTraceIdForUpdate(incomingTrace.getTraceId())
                .orElse(incomingTrace);
        String previousRootSpanId = storedTrace.getRootSpanId();

        if (storedTrace != incomingTrace) {
            mergeSpans(storedTrace, incomingTrace.getSpans());
        }

        recomputeSummary(storedTrace, incomingTrace, previousRootSpanId);
        TraceEntity savedTrace = traceRepository.saveAndFlush(storedTrace);
        return otlpMapper.mapToTraceResponseDto(savedTrace);
    }

    private void mergeSpans(TraceEntity storedTrace, List<SpanEntity> incomingSpans) {
        Map<String, SpanEntity> storedBySpanId = new LinkedHashMap<>();
        for (SpanEntity storedSpan : storedTrace.getSpans()) {
            SpanEntity duplicate = storedBySpanId.putIfAbsent(storedSpan.getSpanId(), storedSpan);
            if (duplicate != null) {
                throw new IllegalStateException(
                        "Stored trace " + storedTrace.getTraceId() + " contains duplicate span " + storedSpan.getSpanId()
                );
            }
        }

        for (SpanEntity incomingSpan : incomingSpans) {
            SpanEntity storedSpan = storedBySpanId.get(incomingSpan.getSpanId());
            if (storedSpan == null) {
                storedTrace.addSpan(incomingSpan);
                storedBySpanId.put(incomingSpan.getSpanId(), incomingSpan);
            } else {
                storedSpan.mergeFrom(incomingSpan);
            }
        }
    }

    private void recomputeSummary(TraceEntity trace, TraceEntity incomingTrace, String previousRootSpanId) {
        List<SpanEntity> spans = trace.getSpans();
        if (spans.isEmpty()) {
            throw new IllegalArgumentException("An OTLP trace must contain at least one valid span");
        }

        SpanEntity rootSpan = spans.stream()
                .min(Comparator
                        .comparingInt(this::rootRank)
                        .thenComparing(SpanEntity::getStartTime)
                        .thenComparing(SpanEntity::getSpanId))
                .orElseThrow();

        LocalDateTime startTime = spans.stream()
                .map(SpanEntity::getStartTime)
                .min(LocalDateTime::compareTo)
                .orElse(rootSpan.getStartTime());
        LocalDateTime endTime = spans.stream()
                .map(SpanEntity::getEndTime)
                .max(LocalDateTime::compareTo)
                .orElse(rootSpan.getEndTime());
        long durationMs = Math.max(0L, Duration.between(startTime, endTime).toMillis());
        boolean hasError = spans.stream().anyMatch(span -> "ERROR".equalsIgnoreCase(span.getStatusCode()));
        long longestSpanMs = spans.stream()
                .map(SpanEntity::getDurationMs)
                .filter(value -> value != null)
                .mapToLong(Long::longValue)
                .max()
                .orElse(0L);

        boolean incomingDefinesRoot = rootSpan.getSpanId().equals(incomingTrace.getRootSpanId());
        boolean previousRootStillCanonical = rootSpan.getSpanId().equals(previousRootSpanId);
        boolean canonicalRootChanged = !Objects.equals(rootSpan.getSpanId(), previousRootSpanId);
        String requestUri = rootSummaryValue(
                trace.getRequestUri(),
                incomingTrace.getRequestUri(),
                rootSpan.getName(),
                incomingDefinesRoot,
                previousRootStillCanonical
        );
        String businessOperation = rootSummaryValue(
                trace.getBusinessOperation(),
                incomingTrace.getBusinessOperation(),
                rootSpan.getName(),
                incomingDefinesRoot,
                previousRootStillCanonical
        );

        trace.setServiceName(rootSpan.getServiceName());
        trace.setRootSpanName(rootSpan.getName());
        trace.setRootSpanId(rootSpan.getSpanId());
        trace.setStartTime(startTime);
        trace.setEndTime(endTime);
        trace.setDurationMs(durationMs);
        trace.setStatusCode(hasError ? "ERROR" : "OK");
        trace.setStatusMessage(errorMessage(spans, rootSpan));
        trace.setHttpMethod(rootSpan.getHttpMethod() != null ? rootSpan.getHttpMethod() : "OTLP");
        trace.setRequestUri(requestUri != null ? requestUri : rootSpan.getName());
        trace.setBusinessOperation(businessOperation != null ? businessOperation : rootSpan.getName());
        trace.setEntryEndpoint(trace.getRequestUri());
        trace.setExitStatus(rootSpan.getHttpStatusCode() != null
                ? String.valueOf(rootSpan.getHttpStatusCode())
                : trace.getStatusCode());
        trace.setCriticalPathDurationMs(longestSpanMs);
        trace.setTimelineSummary(spans.size() + " spans across " + serviceCount(spans) + " services in " + durationMs + "ms");
        trace.setEvidenceGraphId("trace:" + trace.getTraceId());
        mergeResourceMetadata(trace, incomingTrace, rootSpan, incomingDefinesRoot, canonicalRootChanged);
    }

    private void mergeResourceMetadata(
            TraceEntity trace,
            TraceEntity incomingTrace,
            SpanEntity rootSpan,
            boolean incomingDefinesRoot,
            boolean canonicalRootChanged
    ) {
        boolean sameCanonicalService = Objects.equals(rootSpan.getServiceName(), incomingTrace.getServiceName());
        if (!incomingDefinesRoot && !sameCanonicalService) {
            return;
        }

        if (canonicalRootChanged && incomingDefinesRoot) {
            trace.setEnvironment(preferObserved(incomingTrace.getEnvironment(), trace.getEnvironment()));
            trace.setServiceVersion(preferObserved(incomingTrace.getServiceVersion(), trace.getServiceVersion()));
            trace.setSdkLanguage(preferObserved(incomingTrace.getSdkLanguage(), trace.getSdkLanguage()));
            return;
        }

        trace.setEnvironment(enrichMissing(trace.getEnvironment(), incomingTrace.getEnvironment()));
        trace.setServiceVersion(enrichMissing(trace.getServiceVersion(), incomingTrace.getServiceVersion()));
        trace.setSdkLanguage(enrichMissing(trace.getSdkLanguage(), incomingTrace.getSdkLanguage()));
    }

    private String rootSummaryValue(
            String existing,
            String incoming,
            String rootFallback,
            boolean incomingDefinesRoot,
            boolean previousRootStillCanonical
    ) {
        if (incomingDefinesRoot && isObserved(incoming)) {
            return incoming.trim();
        }
        if (previousRootStillCanonical && isObserved(existing)) {
            return existing;
        }
        return rootFallback;
    }

    private String enrichMissing(String existing, String incoming) {
        return isObserved(existing) ? existing : preferObserved(incoming, null);
    }

    private String preferObserved(String preferred, String fallback) {
        return isObserved(preferred) ? preferred.trim() : fallback;
    }

    private boolean isObserved(String value) {
        return value != null && !value.isBlank();
    }

    private int rootRank(SpanEntity span) {
        boolean hasNoParent = span.getParentSpanId() == null || span.getParentSpanId().isBlank();
        if (hasNoParent && "SERVER".equalsIgnoreCase(span.getKind())) {
            return 0;
        }
        return hasNoParent ? 1 : 2;
    }

    private String errorMessage(List<SpanEntity> spans, SpanEntity rootSpan) {
        return spans.stream()
                .filter(span -> "ERROR".equalsIgnoreCase(span.getStatusCode()))
                .map(SpanEntity::getStatusMessage)
                .filter(message -> message != null && !message.isBlank())
                .findFirst()
                .orElse(rootSpan.getStatusMessage());
    }

    private long serviceCount(List<SpanEntity> spans) {
        return spans.stream()
                .map(SpanEntity::getServiceName)
                .filter(service -> service != null && !service.isBlank())
                .distinct()
                .count();
    }
}
