package com.argusiq.tracing.dto;

import java.time.LocalDateTime;
import java.util.List;

public class ServiceResponse {

    private final Long id;
    private final String serviceName;
    private final String health;
    private final double averageLatencyMs;
    private final double p95LatencyMs;
    private final double p99LatencyMs;
    private final double errorRate;
    private final long throughput;
    private final long activeEndpoints;
    private final long dependencyCount;
    private final List<TraceResponseDto> recentTraces;
    private final List<TraceResponseDto> recentErrors;
    private final double availability;
    private final long serviceUptimeMinutes;
    private final long requestVolume;
    private final long longestRequestMs;
    private final long failureCount;
    private final String slowestOperation;
    private final String fastestOperation;
    private final List<NamedMetricDto> topEndpoints;
    private final List<NamedMetricDto> mostCommonErrors;
    private final LocalDateTime firstSeen;
    private final LocalDateTime lastSeen;

    public ServiceResponse(
            Long id,
            String serviceName,
            String health,
            double averageLatencyMs,
            double p95LatencyMs,
            double p99LatencyMs,
            double errorRate,
            long throughput,
            long activeEndpoints,
            long dependencyCount,
            List<TraceResponseDto> recentTraces,
            List<TraceResponseDto> recentErrors,
            double availability,
            long serviceUptimeMinutes,
            long requestVolume,
            long longestRequestMs,
            long failureCount,
            String slowestOperation,
            String fastestOperation,
            List<NamedMetricDto> topEndpoints,
            List<NamedMetricDto> mostCommonErrors,
            LocalDateTime firstSeen,
            LocalDateTime lastSeen
    ) {
        this.id = id;
        this.serviceName = serviceName;
        this.health = health;
        this.averageLatencyMs = averageLatencyMs;
        this.p95LatencyMs = p95LatencyMs;
        this.p99LatencyMs = p99LatencyMs;
        this.errorRate = errorRate;
        this.throughput = throughput;
        this.activeEndpoints = activeEndpoints;
        this.dependencyCount = dependencyCount;
        this.recentTraces = recentTraces != null ? recentTraces : List.of();
        this.recentErrors = recentErrors != null ? recentErrors : List.of();
        this.availability = availability;
        this.serviceUptimeMinutes = serviceUptimeMinutes;
        this.requestVolume = requestVolume;
        this.longestRequestMs = longestRequestMs;
        this.failureCount = failureCount;
        this.slowestOperation = slowestOperation;
        this.fastestOperation = fastestOperation;
        this.topEndpoints = topEndpoints != null ? topEndpoints : List.of();
        this.mostCommonErrors = mostCommonErrors != null ? mostCommonErrors : List.of();
        this.firstSeen = firstSeen;
        this.lastSeen = lastSeen;
    }

    public Long getId() { return id; }
    public String getServiceName() { return serviceName; }
    public String getHealth() { return health; }
    public double getAverageLatencyMs() { return averageLatencyMs; }
    public double getP95LatencyMs() { return p95LatencyMs; }
    public double getP99LatencyMs() { return p99LatencyMs; }
    public double getErrorRate() { return errorRate; }
    public long getThroughput() { return throughput; }
    public long getActiveEndpoints() { return activeEndpoints; }
    public long getDependencyCount() { return dependencyCount; }
    public List<TraceResponseDto> getRecentTraces() { return recentTraces; }
    public List<TraceResponseDto> getRecentErrors() { return recentErrors; }
    public double getAvailability() { return availability; }
    public long getServiceUptimeMinutes() { return serviceUptimeMinutes; }
    public long getRequestVolume() { return requestVolume; }
    public long getLongestRequestMs() { return longestRequestMs; }
    public long getFailureCount() { return failureCount; }
    public String getSlowestOperation() { return slowestOperation; }
    public String getFastestOperation() { return fastestOperation; }
    public List<NamedMetricDto> getTopEndpoints() { return topEndpoints; }
    public List<NamedMetricDto> getMostCommonErrors() { return mostCommonErrors; }
    public LocalDateTime getFirstSeen() { return firstSeen; }
    public LocalDateTime getLastSeen() { return lastSeen; }
}
