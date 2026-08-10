package com.argusiq.tracing.service;

import com.argusiq.tracing.dto.AverageResponseTimeDto;
import com.argusiq.tracing.dto.TraceCountDto;
import com.argusiq.tracing.dto.TraceDetailResponseDto;
import com.argusiq.tracing.dto.TraceResponseDto;
import com.argusiq.tracing.entity.SpanEntity;
import com.argusiq.tracing.entity.TraceEntity;
import com.argusiq.tracing.mapper.OtlpMapper;
import com.argusiq.tracing.repository.SpanRepository;
import com.argusiq.tracing.repository.TraceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Service
public class TraceService {

    private static final Logger logger = LoggerFactory.getLogger(TraceService.class);
    private static final String TRACE_TOPIC = "/topic/traces";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final TraceRepository traceRepository;
    private final SpanRepository spanRepository;
    private final ServiceDiscoveryService serviceDiscoveryService;
    private final OtlpMapper otlpMapper;
    private final SimpMessagingTemplate messagingTemplate;

    public TraceService(
            TraceRepository traceRepository,
            SpanRepository spanRepository,
            ServiceDiscoveryService serviceDiscoveryService,
            OtlpMapper otlpMapper,
            SimpMessagingTemplate messagingTemplate
    ) {
        this.traceRepository = traceRepository;
        this.spanRepository = spanRepository;
        this.serviceDiscoveryService = serviceDiscoveryService;
        this.otlpMapper = otlpMapper;
        this.messagingTemplate = messagingTemplate;
    }

    @Transactional
    public TraceEntity saveHttpRequestTrace(
            String httpMethod,
            String requestUri,
            Long executionTimeMs,
            LocalDateTime timestamp,
            Integer httpStatusCode
    ) {
        LocalDateTime endTime = timestamp != null ? timestamp : LocalDateTime.now(ZoneOffset.UTC);
        long durationMs = executionTimeMs != null ? executionTimeMs : 0L;
        LocalDateTime startTime = endTime.minusNanos(durationMs * 1_000_000L);
        String traceId = randomHex(16);
        String spanId = randomHex(8);
        String serviceName = "argusiq-backend";
        String operation = normalizeMethod(httpMethod) + " " + normalizePath(requestUri);
        String traceStatus = httpStatusCode != null && httpStatusCode >= 500 ? "ERROR" : "OK";

        serviceDiscoveryService.discoverService(serviceName, "production", null, "java");

        TraceEntity trace = new TraceEntity(
                traceId,
                serviceName,
                operation,
                startTime,
                endTime,
                durationMs,
                traceStatus,
                null,
                normalizeMethod(httpMethod),
                normalizePath(requestUri)
        );
        trace.setBusinessOperation(operation);
        trace.setRootSpanId(spanId);
        trace.setEntryEndpoint(normalizePath(requestUri));
        trace.setExitStatus(httpStatusCode != null ? String.valueOf(httpStatusCode) : traceStatus);
        trace.setCriticalPathDurationMs(durationMs);
        trace.setTimelineSummary(operation + " completed in " + durationMs + "ms");
        trace.setEvidenceGraphId("trace:" + traceId);

        SpanEntity rootSpan = new SpanEntity(
                spanId,
                traceId,
                null,
                operation,
                "SERVER",
                startTime,
                endTime,
                durationMs,
                traceStatus,
                null,
                serviceName
        );
        rootSpan.setHttpMethod(normalizeMethod(httpMethod));
        rootSpan.setHttpStatusCode(httpStatusCode);
        trace.addSpan(rootSpan);

        TraceEntity saved = traceRepository.save(trace);
        TraceResponseDto dto = otlpMapper.mapToTraceResponseDto(saved);
        messagingTemplate.convertAndSend(TRACE_TOPIC, dto);
        logger.info("Captured canonical HTTP trace traceId={} {} {}", traceId, httpMethod, requestUri);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<TraceResponseDto> getAllTraces() {
        return traceRepository.findAll(Sort.by(Sort.Direction.DESC, "startTime"))
                .stream()
                .map(otlpMapper::mapToTraceResponseDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TraceResponseDto> getSlowestTraces() {
        return traceRepository.findTop5ByOrderByDurationMsDesc()
                .stream()
                .map(otlpMapper::mapToTraceResponseDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<TraceDetailResponseDto> getTraceByTraceId(String traceId) {
        if (traceId == null || traceId.trim().isEmpty()) {
            return Optional.empty();
        }

        String cleanedTraceId = traceId.trim();
        Optional<TraceEntity> trace = traceRepository.findByTraceIdWithSpans(cleanedTraceId);
        if (trace.isPresent()) {
            return trace.map(otlpMapper::mapToTraceDetailResponseDto);
        }

        try {
            return traceRepository.findById(Long.parseLong(cleanedTraceId))
                    .map(otlpMapper::mapToTraceDetailResponseDto);
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    @Transactional(readOnly = true)
    public AverageResponseTimeDto getAverageResponseTime() {
        Double avg = traceRepository.findAverageDurationMs();
        return new AverageResponseTimeDto(avg != null ? avg : 0.0);
    }

    @Transactional(readOnly = true)
    public TraceCountDto getTraceCount() {
        return new TraceCountDto(traceRepository.count());
    }

    @Transactional(readOnly = true)
    public List<TraceResponseDto> recentTracesForService(String serviceName, int limit) {
        return traceRepository.findRecentByServiceName(serviceName, PageRequest.of(0, Math.max(1, limit)))
                .stream()
                .map(otlpMapper::mapToTraceResponseDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TraceResponseDto> recentErrorsForService(String serviceName, int limit) {
        return traceRepository.findRecentErrorsByServiceName(serviceName, PageRequest.of(0, Math.max(1, limit)))
                .stream()
                .map(otlpMapper::mapToTraceResponseDto)
                .toList();
    }

    private String randomHex(int byteCount) {
        byte[] bytes = new byte[byteCount];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private String normalizeMethod(String httpMethod) {
        return httpMethod != null && !httpMethod.isBlank() ? httpMethod.trim().toUpperCase() : "HTTP";
    }

    private String normalizePath(String requestUri) {
        return requestUri != null && !requestUri.isBlank() ? requestUri.trim() : "/";
    }
}
