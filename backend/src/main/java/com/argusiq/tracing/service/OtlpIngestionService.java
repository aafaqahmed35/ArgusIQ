package com.argusiq.tracing.service;

import com.argusiq.tracing.dto.TraceResponseDto;
import com.argusiq.tracing.entity.SpanEntity;
import com.argusiq.tracing.entity.TraceEntity;
import com.argusiq.tracing.mapper.OtlpMapper;
import com.google.protobuf.InvalidProtocolBufferException;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceResponse;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.resource.v1.Resource;
import io.opentelemetry.proto.trace.v1.ResourceSpans;
import io.opentelemetry.proto.trace.v1.ScopeSpans;
import io.opentelemetry.proto.trace.v1.Span;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

@Service
public class OtlpIngestionService {

    private static final Logger logger = LoggerFactory.getLogger(OtlpIngestionService.class);
    private static final String TRACE_TOPIC = "/topic/traces";
    private static final int MAX_MERGE_ATTEMPTS = 3;

    private final ServiceDiscoveryService serviceDiscoveryService;
    private final OtlpTraceMergeService traceMergeService;
    private final OtlpMapper otlpMapper;
    private final SimpMessagingTemplate messagingTemplate;

    public OtlpIngestionService(
            ServiceDiscoveryService serviceDiscoveryService,
            OtlpTraceMergeService traceMergeService,
            OtlpMapper otlpMapper,
            SimpMessagingTemplate messagingTemplate
    ) {
        this.serviceDiscoveryService = serviceDiscoveryService;
        this.traceMergeService = traceMergeService;
        this.otlpMapper = otlpMapper;
        this.messagingTemplate = messagingTemplate;
    }

    public ExportTraceServiceResponse ingestProtobufTraces(byte[] payload) throws InvalidProtocolBufferException {
        if (payload == null || payload.length == 0) {
            logger.debug("Received empty OTLP payload");
            return ExportTraceServiceResponse.getDefaultInstance();
        }

        byte[] actualPayload = payload;

        if (isGzipCompressed(payload)) {
            try (GZIPInputStream gzipInputStream = new GZIPInputStream(new ByteArrayInputStream(payload))) {
                actualPayload = gzipInputStream.readAllBytes();
                logger.debug("Decompressed GZIP OTLP payload from {} bytes to {} bytes", payload.length, actualPayload.length);
            } catch (IOException e) {
                logger.error("Failed to decompress GZIP OTLP payload: {}", e.getMessage());
                throw new InvalidProtocolBufferException("GZIP decompression failed: " + e.getMessage());
            }
        }

        ExportTraceServiceRequest request = ExportTraceServiceRequest.parseFrom(actualPayload);

        if (request.getResourceSpansCount() == 0) {
            logger.debug("Received empty ExportTraceServiceRequest");
            return ExportTraceServiceResponse.getDefaultInstance();
        }

        Map<String, List<PendingSpan>> traceSpanMap = new HashMap<>();

        for (ResourceSpans resourceSpans : request.getResourceSpansList()) {
            Resource resource = resourceSpans.getResource();
            List<KeyValue> resourceAttrs = resource.getAttributesList();

            String serviceName = otlpMapper.getAttributeValue(resourceAttrs, "service.name");
            if (serviceName == null || serviceName.isEmpty()) {
                serviceName = "unknown-service";
            }

            String environment = otlpMapper.getAttributeValue(resourceAttrs, "deployment.environment");
            if (environment == null) {
                environment = otlpMapper.getAttributeValue(resourceAttrs, "environment");
            }

            String version = otlpMapper.getAttributeValue(resourceAttrs, "service.version");
            String language = otlpMapper.getAttributeValue(resourceAttrs, "telemetry.sdk.language");
            if (language == null) {
                language = otlpMapper.getAttributeValue(resourceAttrs, "process.runtime.name");
            }

            discoverService(serviceName, environment, version, language);

            for (ScopeSpans scopeSpans : resourceSpans.getScopeSpansList()) {
                for (Span span : scopeSpans.getSpansList()) {
                    String traceId = otlpMapper.bytesToHex(span.getTraceId());
                    if (traceId != null && !traceId.isEmpty()) {
                        traceSpanMap.computeIfAbsent(traceId, k -> new ArrayList<>()).add(new PendingSpan(span, serviceName));
                    }
                }
            }
        }

        for (Map.Entry<String, List<PendingSpan>> entry : traceSpanMap.entrySet()) {
            processTrace(entry.getKey(), entry.getValue());
        }

        return ExportTraceServiceResponse.getDefaultInstance();
    }

    private boolean isGzipCompressed(byte[] payload) {
        return payload != null && payload.length >= 2
                && (payload[0] == (byte) 0x1f)
                && (payload[1] == (byte) 0x8b);
    }

    private void processTrace(String traceId, List<PendingSpan> pendingSpans) {
        List<PendingSpan> uniquePendingSpans = deduplicateBatch(pendingSpans);
        if (uniquePendingSpans.isEmpty()) {
            return;
        }

        TraceResponseDto dto = mergeTraceWithRetry(traceId, uniquePendingSpans);
        logger.info(
                "Merged OTLP traceId={} with {} incoming spans; canonical span count={}",
                traceId,
                uniquePendingSpans.size(),
                dto.getSpanCount()
        );

        messagingTemplate.convertAndSend(TRACE_TOPIC, dto);
        logger.info("Broadcasted committed OTLP trace {} to {}", dto.getId(), TRACE_TOPIC);
    }

    private TraceEntity buildIncomingTrace(String traceId, List<PendingSpan> pendingSpans) {
        List<Span> spans = pendingSpans.stream().map(PendingSpan::span).toList();

        PendingSpan rootPendingSpan = pendingSpans.stream()
                .filter(s -> "SPAN_KIND_SERVER".equals(s.span().getKind().name()))
                .filter(s -> s.span().getParentSpanId() == null || s.span().getParentSpanId().isEmpty())
                .findFirst()
                .or(() -> pendingSpans.stream().filter(s -> s.span().getParentSpanId() == null || s.span().getParentSpanId().isEmpty()).findFirst())
                .orElse(pendingSpans.stream()
                        .min(Comparator.comparingLong(s -> s.span().getStartTimeUnixNano()))
                        .orElse(pendingSpans.get(0)));
        Span rootSpan = rootPendingSpan.span();
        String serviceName = rootPendingSpan.serviceName();

        long rootServerCount = pendingSpans.stream()
                .filter(s -> "SPAN_KIND_SERVER".equals(s.span().getKind().name()))
                .filter(s -> s.span().getParentSpanId() == null || s.span().getParentSpanId().isEmpty())
                .count();
        if (rootServerCount > 1) {
            logger.warn("Trace {} contains {} root SERVER spans; canonical root selected as {}", traceId, rootServerCount, otlpMapper.bytesToHex(rootSpan.getSpanId()));
        }

        Span longestSpan = spans.stream()
                .max(Comparator.comparingLong(s -> Math.max(0L, s.getEndTimeUnixNano() - s.getStartTimeUnixNano())))
                .orElse(spans.get(0));

        String rootSpanName = (rootSpan.getName() != null && !rootSpan.getName().isEmpty()) ? rootSpan.getName() : "HTTP Request";

        List<KeyValue> spanAttrs = rootSpan.getAttributesList();
        String httpMethod = otlpMapper.firstAttributeValue(spanAttrs, "http.method", "http.request.method");
        if (httpMethod == null) {
            httpMethod = "OTLP";
        }

        String requestUri = otlpMapper.firstAttributeValue(spanAttrs, "http.target", "url.path", "http.url", "url.full");
        if (requestUri == null) {
            requestUri = rootSpanName;
        }

        Integer exitStatus = otlpMapper.parseInteger(otlpMapper.firstAttributeValue(spanAttrs, "http.status_code", "http.response.status_code"));
        String businessOperation = otlpMapper.firstAttributeValue(spanAttrs, "business.operation", "argusiq.business_operation");
        if (businessOperation == null) {
            businessOperation = rootSpanName;
        }

        long minStartTimeNano = spans.stream()
                .mapToLong(Span::getStartTimeUnixNano)
                .filter(t -> t > 0)
                .min()
                .orElse(System.currentTimeMillis() * 1_000_000L);

        long maxEndTimeNano = spans.stream()
                .mapToLong(Span::getEndTimeUnixNano)
                .filter(t -> t > 0)
                .max()
                .orElse(minStartTimeNano);

        LocalDateTime startTime = otlpMapper.nanoToLocalDateTime(minStartTimeNano);
        LocalDateTime endTime = otlpMapper.nanoToLocalDateTime(maxEndTimeNano);
        long durationMs = (maxEndTimeNano > minStartTimeNano) ? (maxEndTimeNano - minStartTimeNano) / 1_000_000L : 0L;

        boolean hasError = spans.stream().anyMatch(s -> s.getStatus().getCode().name().contains("ERROR"));
        String statusCode = hasError ? "ERROR" : "OK";
        String statusMessage = rootSpan.getStatus().getMessage();
        TraceEntity traceEntity = new TraceEntity(
                traceId,
                serviceName,
                rootSpanName,
                startTime,
                endTime,
                durationMs,
                statusCode,
                statusMessage,
                httpMethod,
                requestUri
        );
        traceEntity.setRootSpanId(otlpMapper.bytesToHex(rootSpan.getSpanId()));
        traceEntity.setBusinessOperation(businessOperation);
        traceEntity.setEntryEndpoint(requestUri);
        traceEntity.setExitStatus(exitStatus != null ? String.valueOf(exitStatus) : statusCode);
        traceEntity.setCriticalPathDurationMs(Math.max(0L, (longestSpan.getEndTimeUnixNano() - longestSpan.getStartTimeUnixNano()) / 1_000_000L));
        traceEntity.setTimelineSummary(buildTimelineSummary(pendingSpans, durationMs));
        traceEntity.setEvidenceGraphId("trace:" + traceId);

        for (PendingSpan pendingSpan : pendingSpans) {
            SpanEntity spanEntity = otlpMapper.mapToSpanEntity(pendingSpan.span(), pendingSpan.serviceName());
            traceEntity.addSpan(spanEntity);
        }
        return traceEntity;
    }

    private List<PendingSpan> deduplicateBatch(List<PendingSpan> pendingSpans) {
        if (pendingSpans == null || pendingSpans.isEmpty()) {
            return List.of();
        }

        Map<String, PendingSpan> spansById = new LinkedHashMap<>();
        for (PendingSpan pendingSpan : pendingSpans) {
            String spanId = otlpMapper.bytesToHex(pendingSpan.span().getSpanId());
            if (spanId == null || spanId.isBlank()) {
                logger.warn("Ignoring OTLP span without a valid span ID");
                continue;
            }
            spansById.put(spanId, pendingSpan);
        }
        return List.copyOf(spansById.values());
    }

    private TraceResponseDto mergeTraceWithRetry(String traceId, List<PendingSpan> pendingSpans) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_MERGE_ATTEMPTS; attempt++) {
            try {
                return traceMergeService.mergeTrace(buildIncomingTrace(traceId, pendingSpans));
            } catch (DataIntegrityViolationException | PessimisticLockingFailureException ex) {
                lastFailure = ex;
                logger.warn(
                        "Retrying OTLP merge for traceId={} after concurrent write conflict ({}/{})",
                        traceId,
                        attempt,
                        MAX_MERGE_ATTEMPTS
                );
            }
        }
        throw lastFailure != null ? lastFailure : new IllegalStateException("OTLP trace merge failed");
    }

    private void discoverService(String serviceName, String environment, String version, String language) {
        try {
            serviceDiscoveryService.discoverService(serviceName, environment, version, language);
        } catch (DataIntegrityViolationException firstInsertRace) {
            serviceDiscoveryService.discoverService(serviceName, environment, version, language);
        }
    }

    private String buildTimelineSummary(List<PendingSpan> pendingSpans, long durationMs) {
        long serviceCount = pendingSpans.stream().map(PendingSpan::serviceName).distinct().count();
        return pendingSpans.size() + " spans across " + serviceCount + " services in " + durationMs + "ms";
    }

    private record PendingSpan(Span span, String serviceName) {
    }
}
