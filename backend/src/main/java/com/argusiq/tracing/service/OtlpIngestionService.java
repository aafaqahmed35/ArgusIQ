package com.argusiq.tracing.service;

import com.argusiq.tracing.dto.TraceResponseDto;
import com.argusiq.tracing.entity.SpanEntity;
import com.argusiq.tracing.entity.TraceEntity;
import com.argusiq.tracing.mapper.OtlpMapper;
import com.argusiq.tracing.repository.TraceRepository;
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
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

@Service
public class OtlpIngestionService {

    private static final Logger logger = LoggerFactory.getLogger(OtlpIngestionService.class);
    private static final String TRACE_TOPIC = "/topic/traces";

    private final TraceRepository traceRepository;
    private final ServiceDiscoveryService serviceDiscoveryService;
    private final OtlpMapper otlpMapper;
    private final SimpMessagingTemplate messagingTemplate;

    public OtlpIngestionService(
            TraceRepository traceRepository,
            ServiceDiscoveryService serviceDiscoveryService,
            OtlpMapper otlpMapper,
            SimpMessagingTemplate messagingTemplate
    ) {
        this.traceRepository = traceRepository;
        this.serviceDiscoveryService = serviceDiscoveryService;
        this.otlpMapper = otlpMapper;
        this.messagingTemplate = messagingTemplate;
    }

    @Transactional
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

            serviceDiscoveryService.discoverService(serviceName, environment, version, language);

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
        List<Span> spans = pendingSpans != null
                ? pendingSpans.stream().map(PendingSpan::span).toList()
                : List.of();
        if (spans == null || spans.isEmpty()) {
            return;
        }

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
        final String finalHttpMethod = httpMethod;
        final String finalRequestUri = requestUri;

        TraceEntity traceEntity = traceRepository.findByTraceIdWithSpans(traceId)
                .orElseGet(() -> new TraceEntity(
                        traceId,
                        serviceName,
                        rootSpanName,
                        startTime,
                        endTime,
                        durationMs,
                        statusCode,
                        statusMessage,
                        finalHttpMethod,
                        finalRequestUri
                ));
        traceEntity.setServiceName(serviceName);
        traceEntity.setRootSpanName(rootSpanName);
        traceEntity.setStartTime(startTime);
        traceEntity.setEndTime(endTime);
        traceEntity.setDurationMs(durationMs);
        traceEntity.setStatusCode(statusCode);
        traceEntity.setStatusMessage(statusMessage);
        traceEntity.setHttpMethod(httpMethod);
        traceEntity.setRequestUri(requestUri);
        traceEntity.setRootSpanId(otlpMapper.bytesToHex(rootSpan.getSpanId()));
        traceEntity.setBusinessOperation(businessOperation);
        traceEntity.setEntryEndpoint(requestUri);
        traceEntity.setExitStatus(exitStatus != null ? String.valueOf(exitStatus) : statusCode);
        traceEntity.setCriticalPathDurationMs(Math.max(0L, (longestSpan.getEndTimeUnixNano() - longestSpan.getStartTimeUnixNano()) / 1_000_000L));
        traceEntity.setTimelineSummary(buildTimelineSummary(pendingSpans, durationMs));
        traceEntity.setEvidenceGraphId("trace:" + traceId);
        traceEntity.clearSpans();

        for (PendingSpan pendingSpan : pendingSpans) {
            SpanEntity spanEntity = otlpMapper.mapToSpanEntity(pendingSpan.span(), pendingSpan.serviceName());
            traceEntity.addSpan(spanEntity);
        }

        TraceEntity savedTrace = traceRepository.save(traceEntity);
        logger.info("Ingested OTLP traceId={} with {} spans across {} services", traceId, spans.size(), pendingSpans.stream().map(PendingSpan::serviceName).distinct().count());

        TraceResponseDto dto = otlpMapper.mapToTraceResponseDto(savedTrace);
        messagingTemplate.convertAndSend(TRACE_TOPIC, dto);
        logger.info("Broadcasted OTLP trace {} to {}", savedTrace.getId(), TRACE_TOPIC);
    }

    private String buildTimelineSummary(List<PendingSpan> pendingSpans, long durationMs) {
        long serviceCount = pendingSpans.stream().map(PendingSpan::serviceName).distinct().count();
        return pendingSpans.size() + " spans across " + serviceCount + " services in " + durationMs + "ms";
    }

    private record PendingSpan(Span span, String serviceName) {
    }
}
