package com.argusiq.tracing.dto;

import java.util.List;
import java.util.Map;

public class MetricsResponse {

    private final double averageLatencyMs;
    private final double medianLatencyMs;
    private final double p50LatencyMs;
    private final double p90LatencyMs;
    private final double p95LatencyMs;
    private final double p99LatencyMs;
    private final long minimumLatencyMs;
    private final long maximumLatencyMs;
    private final double errorRate;
    private final double successRate;
    private final long throughput;
    private final long requestsPerMinute;
    private final long requestsPerHour;
    private final long requestsPerDay;
    private final long uniqueEndpoints;
    private final long uniqueServices;
    private final List<NamedMetricDto> slowEndpointRanking;
    private final List<NamedMetricDto> fastEndpointRanking;
    private final List<NamedMetricDto> mostFailingEndpoints;
    private final List<NamedMetricDto> mostActiveServices;
    private final List<NamedMetricDto> mostExpensiveOperations;
    private final Map<String, Long> traceDurationHistogram;
    private final Map<String, Long> statusCodeDistribution;
    private final Map<String, Long> httpMethodDistribution;

    public MetricsResponse(
            double averageLatencyMs,
            double medianLatencyMs,
            double p50LatencyMs,
            double p90LatencyMs,
            double p95LatencyMs,
            double p99LatencyMs,
            long minimumLatencyMs,
            long maximumLatencyMs,
            double errorRate,
            double successRate,
            long throughput,
            long requestsPerMinute,
            long requestsPerHour,
            long requestsPerDay,
            long uniqueEndpoints,
            long uniqueServices,
            List<NamedMetricDto> slowEndpointRanking,
            List<NamedMetricDto> fastEndpointRanking,
            List<NamedMetricDto> mostFailingEndpoints,
            List<NamedMetricDto> mostActiveServices,
            List<NamedMetricDto> mostExpensiveOperations,
            Map<String, Long> traceDurationHistogram,
            Map<String, Long> statusCodeDistribution,
            Map<String, Long> httpMethodDistribution
    ) {
        this.averageLatencyMs = averageLatencyMs;
        this.medianLatencyMs = medianLatencyMs;
        this.p50LatencyMs = p50LatencyMs;
        this.p90LatencyMs = p90LatencyMs;
        this.p95LatencyMs = p95LatencyMs;
        this.p99LatencyMs = p99LatencyMs;
        this.minimumLatencyMs = minimumLatencyMs;
        this.maximumLatencyMs = maximumLatencyMs;
        this.errorRate = errorRate;
        this.successRate = successRate;
        this.throughput = throughput;
        this.requestsPerMinute = requestsPerMinute;
        this.requestsPerHour = requestsPerHour;
        this.requestsPerDay = requestsPerDay;
        this.uniqueEndpoints = uniqueEndpoints;
        this.uniqueServices = uniqueServices;
        this.slowEndpointRanking = slowEndpointRanking != null ? slowEndpointRanking : List.of();
        this.fastEndpointRanking = fastEndpointRanking != null ? fastEndpointRanking : List.of();
        this.mostFailingEndpoints = mostFailingEndpoints != null ? mostFailingEndpoints : List.of();
        this.mostActiveServices = mostActiveServices != null ? mostActiveServices : List.of();
        this.mostExpensiveOperations = mostExpensiveOperations != null ? mostExpensiveOperations : List.of();
        this.traceDurationHistogram = traceDurationHistogram != null ? traceDurationHistogram : Map.of();
        this.statusCodeDistribution = statusCodeDistribution != null ? statusCodeDistribution : Map.of();
        this.httpMethodDistribution = httpMethodDistribution != null ? httpMethodDistribution : Map.of();
    }

    public double getAverageLatencyMs() { return averageLatencyMs; }
    public double getMedianLatencyMs() { return medianLatencyMs; }
    public double getP50LatencyMs() { return p50LatencyMs; }
    public double getP90LatencyMs() { return p90LatencyMs; }
    public double getP95LatencyMs() { return p95LatencyMs; }
    public double getP99LatencyMs() { return p99LatencyMs; }
    public long getMinimumLatencyMs() { return minimumLatencyMs; }
    public long getMaximumLatencyMs() { return maximumLatencyMs; }
    public double getErrorRate() { return errorRate; }
    public double getSuccessRate() { return successRate; }
    public long getThroughput() { return throughput; }
    public long getRequestsPerMinute() { return requestsPerMinute; }
    public long getRequestsPerHour() { return requestsPerHour; }
    public long getRequestsPerDay() { return requestsPerDay; }
    public long getUniqueEndpoints() { return uniqueEndpoints; }
    public long getUniqueServices() { return uniqueServices; }
    public List<NamedMetricDto> getSlowEndpointRanking() { return slowEndpointRanking; }
    public List<NamedMetricDto> getFastEndpointRanking() { return fastEndpointRanking; }
    public List<NamedMetricDto> getMostFailingEndpoints() { return mostFailingEndpoints; }
    public List<NamedMetricDto> getMostActiveServices() { return mostActiveServices; }
    public List<NamedMetricDto> getMostExpensiveOperations() { return mostExpensiveOperations; }
    public Map<String, Long> getTraceDurationHistogram() { return traceDurationHistogram; }
    public Map<String, Long> getStatusCodeDistribution() { return statusCodeDistribution; }
    public Map<String, Long> getHttpMethodDistribution() { return httpMethodDistribution; }
}
