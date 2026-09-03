package com.argusiq.tracing;

import com.argusiq.AbstractPostgresIntegrationTest;
import com.argusiq.tracing.dto.EndpointMetricDto;
import com.argusiq.tracing.dto.MetricsResponse;
import com.argusiq.tracing.entity.MonitoredService;
import com.argusiq.tracing.entity.SpanEntity;
import com.argusiq.tracing.entity.TraceEntity;
import com.argusiq.tracing.repository.MonitoredServiceRepository;
import com.argusiq.tracing.repository.TraceRepository;
import com.argusiq.tracing.service.MetricsService;
import com.argusiq.tracing.service.OtlpTraceMergeService;
import com.argusiq.tracing.service.TraceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MetricsSemanticsPostgresIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private TraceRepository traceRepository;

    @Autowired
    private MonitoredServiceRepository serviceRepository;

    @Autowired
    private MetricsService metricsService;

    @Autowired
    private TraceService traceService;

    @Autowired
    private OtlpTraceMergeService traceMergeService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    private LocalDateTime referenceNow;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE spans, traces, services RESTART IDENTITY CASCADE");
        referenceNow = LocalDateTime.now(ZoneOffset.UTC).minusSeconds(2);
        serviceRepository.save(new MonitoredService("gateway", "test", "1.0", "java", referenceNow.minusDays(2), referenceNow, "ACTIVE"));
        serviceRepository.save(new MonitoredService("worker", "test", "1.0", "java", referenceNow.minusDays(1), referenceNow, "ACTIVE"));

        saveTrace("fast-1", "GET", "/fast", "OK", 50, referenceNow.minusSeconds(20));
        saveTrace("fast-2", "GET", "/fast", "OK", 150, referenceNow.minusMinutes(5));
        saveTrace("slow-1", "POST", "/slow", "ERROR", 500, referenceNow.minusMinutes(30));
        saveTrace("slow-2", "POST", "/slow", "ERROR", 1_000, referenceNow.minusHours(2));
        saveTrace("tie-a", "PUT", "/tie-a", "ERROR", 300, referenceNow.minusDays(2));
        saveTrace("tie-b", "PUT", "/tie-b", "OK", 300, referenceNow.minusSeconds(45));
        saveTrace("future", "DELETE", "/future", "OK", 2_000, referenceNow.plusMinutes(10));
        metricsService.invalidate();
    }

    @Test
    void totalTracesIsThePersistedCountAndThroughputIsAbsentFromJson() throws Exception {
        assertEquals(7, metricsService.getMetrics().totalTraces());

        mockMvc.perform(get("/api/v1/metrics").with(httpBasic("postgres-investigator", INVESTIGATION_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTraces").value(7))
                .andExpect(jsonPath("$.throughput").doesNotExist());
    }

    @Test
    void rollingRequestCountsUseClosedUtcWindowsAndExcludeFutureTraces() {
        MetricsResponse metrics = metricsService.getMetrics();

        assertEquals(2, metrics.requestsPerMinute());
        assertEquals(4, metrics.requestsPerHour());
        assertEquals(5, metrics.requestsPerDay());
    }

    @Test
    void globalLatencyAggregatesUsePostgresPercentileCont() {
        MetricsResponse metrics = metricsService.getMetrics();

        assertEquals(614.2857, metrics.averageLatencyMs(), 0.001);
        assertEquals(300.0, metrics.medianLatencyMs(), 0.001);
        assertEquals(300.0, metrics.p50LatencyMs(), 0.001);
        assertEquals(1_400.0, metrics.p90LatencyMs(), 0.001);
        assertEquals(1_700.0, metrics.p95LatencyMs(), 0.001);
        assertEquals(1_940.0, metrics.p99LatencyMs(), 0.001);
        assertEquals(50L, metrics.minimumLatencyMs());
        assertEquals(2_000L, metrics.maximumLatencyMs());
    }

    @Test
    void errorAndSuccessRatesShareTheTotalTraceDenominator() {
        MetricsResponse metrics = metricsService.getMetrics();

        assertEquals(3, metrics.errorCount());
        assertEquals(3 * 100.0 / 7, metrics.errorRate(), 0.001);
        assertEquals(4 * 100.0 / 7, metrics.successRate(), 0.001);
    }

    @Test
    void zeroTraceDatasetUsesUnknownLatencyAndRateValues() {
        jdbcTemplate.execute("TRUNCATE TABLE spans, traces RESTART IDENTITY CASCADE");
        metricsService.invalidate();

        MetricsResponse metrics = metricsService.getMetrics();
        assertEquals(0, metrics.totalTraces());
        assertNull(metrics.averageLatencyMs());
        assertNull(metrics.p95LatencyMs());
        assertNull(metrics.minimumLatencyMs());
        assertNull(metrics.maximumLatencyMs());
        assertNull(metrics.errorRate());
        assertNull(metrics.successRate());
        assertTrue(metrics.traceDurationHistogram().values().stream().allMatch(value -> value == 0));
    }

    @Test
    void endpointTrafficRankingUsesRequestCountAndDeterministicEndpointTies() {
        List<EndpointMetricDto> endpoints = metricsService.endpointMetrics("traffic", "desc", 100);

        assertEquals(List.of("/fast", "/slow"), endpoints.subList(0, 2).stream().map(EndpointMetricDto::endpoint).toList());
        assertEquals(2, endpoints.getFirst().requestCount());
    }

    @Test
    void endpointLatencyRankingUsesAverageLatencyAndDeterministicEndpointTies() {
        List<EndpointMetricDto> endpoints = metricsService.endpointMetrics("latency", "desc", 100);

        assertEquals("/future", endpoints.getFirst().endpoint());
        assertEquals("/slow", endpoints.get(1).endpoint());
        assertTrue(endpoints.indexOf(endpoint("/tie-a", endpoints)) < endpoints.indexOf(endpoint("/tie-b", endpoints)));
    }

    @Test
    void endpointAggregateIncludesP95ErrorsRatesAndBounds() {
        EndpointMetricDto fast = endpoint("/fast", metricsService.endpointMetrics("traffic", "desc", 100));

        assertEquals(2, fast.requestCount());
        assertEquals(100.0, fast.averageLatencyMs(), 0.001);
        assertEquals(145.0, fast.p95LatencyMs(), 0.001);
        assertEquals(0, fast.errorCount());
        assertEquals(0.0, fast.errorRate(), 0.001);
        assertEquals(50L, fast.minimumLatencyMs());
        assertEquals(150L, fast.maximumLatencyMs());
    }

    @Test
    void failingEndpointRankingUsesErrorCount() {
        List<EndpointMetricDto> endpoints = metricsService.endpointMetrics("errors", "desc", 100);

        assertEquals("/slow", endpoints.getFirst().endpoint());
        assertEquals(2, endpoints.getFirst().errorCount());
        assertEquals("/tie-a", endpoints.get(1).endpoint());
    }

    @Test
    void histogramBoundariesAreDatabaseCounts() {
        MetricsResponse metrics = metricsService.getMetrics();

        assertEquals(1, metrics.traceDurationHistogram().get("0-100ms"));
        assertEquals(1, metrics.traceDurationHistogram().get("101-250ms"));
        assertEquals(3, metrics.traceDurationHistogram().get("251-500ms"));
        assertEquals(1, metrics.traceDurationHistogram().get("501-1000ms"));
        assertEquals(1, metrics.traceDurationHistogram().get("1001-2500ms"));
        assertEquals(0, metrics.traceDurationHistogram().get("2501ms+"));
    }

    @Test
    void methodAndStatusDistributionsCountPersistedTraces() {
        MetricsResponse metrics = metricsService.getMetrics();

        assertEquals(3, metrics.statusCodeDistribution().get("ERROR"));
        assertEquals(4, metrics.statusCodeDistribution().get("OK"));
        assertEquals(2, metrics.httpMethodDistribution().get("GET"));
        assertEquals(2, metrics.httpMethodDistribution().get("POST"));
    }

    @Test
    void slowestOperationsExposeStructuredLatencyAndCountEvidence() {
        MetricsResponse metrics = metricsService.getMetrics();

        assertFalse(metrics.slowestOperations().isEmpty());
        assertEquals("gateway", metrics.slowestOperations().getFirst().serviceName());
        assertEquals("DELETE /future", metrics.slowestOperations().getFirst().operationName());
        assertEquals(2_000.0, metrics.slowestOperations().getFirst().averageLatencyMs());
        assertEquals(1, metrics.slowestOperations().getFirst().observationCount());
    }

    @Test
    void endpointApiValidatesSortAndBoundsResponseSize() throws Exception {
        mockMvc.perform(get("/api/v1/metrics/endpoints")
                        .param("sortBy", "latency")
                        .param("limit", "2")
                        .with(httpBasic("postgres-investigator", INVESTIGATION_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].endpoint").value("/future"))
                .andExpect(jsonPath("$[0].averageLatencyMs").value(2_000.0));

        for (int index = 0; index < 101; index++) {
            saveTrace(
                    "bounded-" + index,
                    "GET",
                    "/bounded-" + index,
                    "OK",
                    10 + index,
                    referenceNow.minusHours(3)
            );
        }

        mockMvc.perform(get("/api/v1/metrics/endpoints")
                        .param("limit", "1000000")
                        .with(httpBasic("postgres-investigator", INVESTIGATION_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(100));

        mockMvc.perform(get("/api/v1/metrics/endpoints")
                        .param("sortBy", "unknown")
                        .with(httpBasic("postgres-investigator", INVESTIGATION_PASSWORD)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/metrics/endpoints")
                        .param("sortDirection", "sideways")
                        .with(httpBasic("postgres-investigator", INVESTIGATION_PASSWORD)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void localTraceCaptureInvalidatesCachedMetricsAfterCommit() {
        assertEquals(7, metricsService.getMetrics().totalTraces());

        traceService.saveHttpRequestTrace("GET", "/cache-local", 25L, referenceNow, 200);

        assertEquals(8, metricsService.getMetrics().totalTraces());
    }

    @Test
    void otlpMergeInvalidatesCachedMetricsAfterCommit() {
        assertEquals(7, metricsService.getMetrics().totalTraces());

        traceMergeService.mergeTrace(trace("cache-otlp", "GET", "/cache-otlp", "OK", 30, referenceNow));

        assertEquals(8, metricsService.getMetrics().totalTraces());
    }

    private EndpointMetricDto endpoint(String endpoint, List<EndpointMetricDto> endpoints) {
        return endpoints.stream().filter(metric -> endpoint.equals(metric.endpoint())).findFirst().orElseThrow();
    }

    private void saveTrace(String traceId, String method, String endpoint, String status, long duration, LocalDateTime startTime) {
        traceRepository.saveAndFlush(trace(traceId, method, endpoint, status, duration, startTime));
    }

    private TraceEntity trace(String traceId, String method, String endpoint, String status, long duration, LocalDateTime startTime) {
        TraceEntity trace = new TraceEntity(
                traceId,
                "gateway",
                method + " " + endpoint,
                startTime,
                startTime.plusNanos(duration * 1_000_000),
                duration,
                status,
                null,
                method,
                endpoint
        );
        trace.setRootSpanId("span-" + traceId);
        SpanEntity span = new SpanEntity(
                "span-" + traceId,
                traceId,
                null,
                method + " " + endpoint,
                "SERVER",
                startTime,
                startTime.plusNanos(duration * 1_000_000),
                duration,
                status,
                null,
                "gateway"
        );
        trace.addSpan(span);
        return trace;
    }
}
