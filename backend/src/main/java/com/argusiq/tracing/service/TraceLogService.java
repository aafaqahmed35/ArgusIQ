package com.argusiq.tracing.service;

import com.argusiq.tracing.dto.AverageResponseTimeDto;
import com.argusiq.tracing.dto.TraceCountDto;
import com.argusiq.tracing.dto.TraceResponseDto;
import com.argusiq.tracing.entity.TraceLog;
import com.argusiq.tracing.repository.TraceLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class TraceLogService {

    private static final Logger logger = LoggerFactory.getLogger(TraceLogService.class);
    private static final String TRACE_TOPIC = "/topic/traces";

    private final TraceLogRepository traceLogRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public TraceLogService(TraceLogRepository traceLogRepository, SimpMessagingTemplate messagingTemplate) {
        this.traceLogRepository = traceLogRepository;
        this.messagingTemplate = messagingTemplate;
    }

    public TraceLog saveTrace(
            String httpMethod,
            String requestUri,
            Long executionTimeMs,
            LocalDateTime timestamp
    ) {
        TraceLog traceLog = new TraceLog(httpMethod, requestUri, executionTimeMs, timestamp);
        TraceLog savedTraceLog = traceLogRepository.save(traceLog);
        TraceResponseDto traceResponse = mapToDto(savedTraceLog);

        System.out.println("SENDING WEBSOCKET EVENT");

        messagingTemplate.convertAndSend(TRACE_TOPIC, traceResponse);

        System.out.println("WEBSOCKET EVENT SENT");

        logger.info("Broadcasted trace {} to {}", traceResponse.getId(), TRACE_TOPIC);

        return savedTraceLog;
    }

    public List<TraceResponseDto> getAllTraces() {
        return traceLogRepository.findAll(Sort.by(Sort.Direction.DESC, "timestamp"))
                .stream()
                .map(this::mapToDto)
                .toList();
    }

    public List<TraceResponseDto> getSlowestTraces() {
        return traceLogRepository.findTop5ByOrderByExecutionTimeMsDesc()
                .stream()
                .map(this::mapToDto)
                .toList();
    }

    public AverageResponseTimeDto getAverageResponseTime() {
        Double averageExecutionTimeMs = traceLogRepository.findAverageExecutionTimeMs();

        if (averageExecutionTimeMs == null) {
            averageExecutionTimeMs = 0.0;
        }

        return new AverageResponseTimeDto(averageExecutionTimeMs);
    }

    public TraceCountDto getTraceCount() {
        return new TraceCountDto(traceLogRepository.count());
    }

    private TraceResponseDto mapToDto(TraceLog traceLog) {
        return new TraceResponseDto(
                traceLog.getId(),
                traceLog.getHttpMethod(),
                traceLog.getRequestUri(),
                traceLog.getExecutionTimeMs(),
                traceLog.getTimestamp()
        );
    }
}
