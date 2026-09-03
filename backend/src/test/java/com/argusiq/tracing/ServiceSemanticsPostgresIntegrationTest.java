package com.argusiq.tracing;

import com.argusiq.AbstractPostgresIntegrationTest;
import com.argusiq.tracing.dto.OperationMetricDto;
import com.argusiq.tracing.dto.ServiceResponse;
import com.argusiq.tracing.entity.MonitoredService;
import com.argusiq.tracing.entity.SpanEntity;
import com.argusiq.tracing.entity.TraceEntity;
import com.argusiq.tracing.repository.MonitoredServiceRepository;
import com.argusiq.tracing.repository.TraceRepository;
import com.argusiq.tracing.service.ServicesBackendService;
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
class ServiceSemanticsPostgresIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private MonitoredServiceRepository serviceRepository;

    @Autowired
    private TraceRepository traceRepository;

    @Autowired
    private ServicesBackendService servicesBackendService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    private LocalDateTime referenceNow;
    private MonitoredService gateway;
    private MonitoredService worker;
    private MonitoredService stale;
    private MonitoredService idle;
    private MonitoredService tied;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE spans, traces, services RESTART IDENTITY CASCADE");
        referenceNow = LocalDateTime.now(ZoneOffset.UTC).minusSeconds(2);
        gateway = saveService("gateway", referenceNow.minusHours(2), referenceNow);
        worker = saveService("worker", referenceNow.minusHours(1), referenceNow);
        stale = saveService("stale-service", referenceNow.minusDays(1), referenceNow.minusMinutes(10));
        idle = saveService("idle-service", referenceNow.minusMinutes(30), referenceNow);
        tied = saveService("tie-service", referenceNow.minusMinutes(20), referenceNow);

        saveSingleSpanTrace("g-fast-1", "gateway", "fast", 100, "OK", referenceNow.minusSeconds(20));
        saveSingleSpanTrace("g-fast-2", "gateway", "fast", 200, "OK", referenceNow.minusSeconds(30));
        saveDependencyTrace();
        saveSingleSpanTrace("g-slow-2", "gateway", "slow", 1_000, "OK", referenceNow.minusMinutes(10));
        saveSingleSpanTrace("g-tie-a", "gateway", "tie-a", 300, "OK", referenceNow.minusMinutes(2));
        saveSingleSpanTrace("g-tie-b", "gateway", "tie-b", 300, "OK", referenceNow.minusMinutes(3));
        saveSingleSpanTrace("stale-request", "stale-service", "old-work", 250, "OK", referenceNow.minusMinutes(10));
        saveSingleSpanTrace("tie-op-a", "tie-service", "op-a", 300, "OK", referenceNow.minusMinutes(2));
        saveSingleSpanTrace("tie-op-b", "tie-service", "op-b", 300, "OK", referenceNow.minusMinutes(3));
    }

    @Test
    void servicesUseDiscoveredIdentityAndMetadata() {
        List<ServiceResponse> services = servicesBackendService.getServices();
        ServiceResponse response = service("gateway", services);

        assertEquals(5, services.size());
        assertEquals(gateway.getId(), response.id());
        assertEquals("test", response.environment());
        assertEquals("1.0", response.version());
        assertEquals("java", response.language());
    }

    @Test
    void serviceRequestMetricsCountServerSpansWithExplicitRates() {
        ServiceResponse response = serviceDetail(gateway);

        assertEquals(6, response.requestCount());
        assertEquals(3, response.requestsPerMinute());
        assertEquals(1, response.errorCount());
        assertEquals(100.0 / 6, response.errorRate(), 0.001);
        assertEquals(500.0 / 6, response.successRate(), 0.001);
    }

    @Test
    void serviceLatencyAndPercentilesAreComputedByPostgres() {
        ServiceResponse response = serviceDetail(gateway);

        assertEquals(450.0, response.averageLatencyMs(), 0.001);
        assertEquals(950.0, response.p95LatencyMs(), 0.001);
        assertEquals(990.0, response.p99LatencyMs(), 0.001);
        assertEquals(100L, response.minimumLatencyMs());
        assertEquals(1_000L, response.maximumLatencyMs());
    }

    @Test
    void noRequestServiceUsesUnknownInsteadOfFabricatedPerfectValues() {
        ServiceResponse response = serviceDetail(idle);

        assertEquals(0, response.requestCount());
        assertEquals(0, response.errorCount());
        assertNull(response.averageLatencyMs());
        assertNull(response.p95LatencyMs());
        assertNull(response.errorRate());
        assertNull(response.successRate());
        assertNull(response.slowestOperation());
        assertNull(response.fastestOperation());
    }

    @Test
    void telemetryStatusDistinguishesErrorsStalenessAndIdleTraffic() {
        assertEquals("ERRORING", serviceDetail(gateway).telemetryStatus());
        assertEquals("ERRORING", serviceDetail(worker).telemetryStatus());
        assertEquals("STALE", serviceDetail(stale).telemetryStatus());
        assertEquals("ACTIVE", serviceDetail(idle).telemetryStatus());
    }

    @Test
    void apiDoesNotClaimAvailabilityUptimeOrAuthoritativeHealth() throws Exception {
        mockMvc.perform(get("/api/v1/services/{id}", gateway.getId())
                        .with(httpBasic("postgres-investigator", INVESTIGATION_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.telemetryStatus").value("ERRORING"))
                .andExpect(jsonPath("$.availability").doesNotExist())
                .andExpect(jsonPath("$.serviceUptimeMinutes").doesNotExist())
                .andExpect(jsonPath("$.health").doesNotExist());
    }

    @Test
    void firstLastSeenAndObservationAgeAreObservationSemantics() {
        ServiceResponse response = serviceDetail(gateway);

        assertEquals(gateway.getFirstSeen(), response.firstSeen());
        assertEquals(gateway.getLastSeen(), response.lastSeen());
        assertTrue(response.observationAgeMinutes() >= 119);
        assertTrue(response.observationAgeMinutes() <= 121);
    }

    @Test
    void fastestAndSlowestOperationsUseAverageObservedSpanLatency() {
        ServiceResponse response = serviceDetail(gateway);

        assertEquals("slow", response.slowestOperation().operationName());
        assertEquals(900.0, response.slowestOperation().averageLatencyMs(), 0.001);
        assertEquals(2, response.slowestOperation().observationCount());
        assertEquals("fast", response.fastestOperation().operationName());
        assertEquals(150.0, response.fastestOperation().averageLatencyMs(), 0.001);
        assertEquals(2, response.fastestOperation().observationCount());
    }

    @Test
    void operationLatencyTiesUseOperationNameDeterministically() {
        ServiceResponse response = serviceDetail(tied);

        assertEquals("op-a", response.slowestOperation().operationName());
        assertEquals("op-a", response.fastestOperation().operationName());
    }

    @Test
    void observedOperationCountAndTrafficRankingDescribeSpanOperations() {
        ServiceResponse response = serviceDetail(gateway);

        assertEquals(4, response.observedOperationCount());
        assertEquals(List.of("fast", "slow"), response.topOperationsByTraffic().subList(0, 2).stream()
                .map(OperationMetricDto::operationName)
                .toList());
    }

    @Test
    void dependencyCountIsComputedFromCrossServiceParentChildSpans() {
        ServiceResponse response = serviceDetail(gateway);

        assertEquals(1, response.dependencyCount());
        assertEquals(List.of("gateway", "worker"), servicesBackendService.getDependencyGraph().getEdges().stream()
                .findFirst()
                .map(edge -> List.of(edge.getSource(), edge.getTarget()))
                .orElseThrow());
    }

    @Test
    void recentServiceTracesIncludeTracesWhereServiceAppearsOnlyInChildSpan() {
        ServiceResponse response = serviceDetail(worker);

        assertEquals(1, response.recentTraces().size());
        assertEquals("g-slow-1", response.recentTraces().getFirst().getTraceId());
        assertEquals(1, response.recentErrors().size());
    }

    @Test
    void serviceListRemainsSummaryOnlyWhileDetailIncludesBoundedRecentActivity() {
        ServiceResponse summary = service("gateway", servicesBackendService.getServices());
        ServiceResponse detail = serviceDetail(gateway);

        assertTrue(summary.recentTraces().isEmpty());
        assertTrue(summary.recentErrors().isEmpty());
        assertEquals(6, detail.recentTraces().size());
        assertEquals(1, detail.recentErrors().size());
    }

    @Test
    void oldAmbiguousServiceFieldsAreAbsentFromPublicJson() throws Exception {
        mockMvc.perform(get("/api/v1/services/{id}", gateway.getId())
                        .with(httpBasic("postgres-investigator", INVESTIGATION_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestCount").value(6))
                .andExpect(jsonPath("$.requestsPerMinute").value(3))
                .andExpect(jsonPath("$.requestVolume").doesNotExist())
                .andExpect(jsonPath("$.throughput").doesNotExist())
                .andExpect(jsonPath("$.activeEndpoints").doesNotExist())
                .andExpect(jsonPath("$.longestRequestMs").doesNotExist())
                .andExpect(jsonPath("$.failureCount").doesNotExist());
    }

    @Test
    void representativePostgresAggregatePlansExecute() {
        String globalPlan = String.join("\n", jdbcTemplate.queryForList("""
                EXPLAIN (ANALYZE, FORMAT TEXT)
                SELECT percentile_cont(0.95) WITHIN GROUP (ORDER BY duration_ms), COUNT(*)
                FROM traces
                """, String.class));
        String servicePlan = String.join("\n", jdbcTemplate.queryForList("""
                EXPLAIN (ANALYZE, FORMAT TEXT)
                SELECT service_name, percentile_cont(0.95) WITHIN GROUP (ORDER BY duration_ms), COUNT(*)
                FROM spans
                WHERE UPPER(kind) = 'SERVER'
                GROUP BY service_name
                """, String.class));

        assertTrue(globalPlan.contains("Aggregate"));
        assertTrue(servicePlan.contains("Aggregate"));
    }

    private MonitoredService saveService(String name, LocalDateTime firstSeen, LocalDateTime lastSeen) {
        return serviceRepository.saveAndFlush(new MonitoredService(name, "test", "1.0", "java", firstSeen, lastSeen, "ACTIVE"));
    }

    private ServiceResponse serviceDetail(MonitoredService service) {
        return servicesBackendService.getService(service.getId()).orElseThrow();
    }

    private ServiceResponse service(String name, List<ServiceResponse> services) {
        return services.stream().filter(service -> name.equals(service.serviceName())).findFirst().orElseThrow();
    }

    private void saveSingleSpanTrace(
            String traceId,
            String serviceName,
            String operation,
            long duration,
            String status,
            LocalDateTime startTime
    ) {
        TraceEntity trace = trace(traceId, serviceName, operation, duration, status, startTime);
        trace.addSpan(span("span-" + traceId, traceId, null, serviceName, operation, duration, status, startTime));
        traceRepository.saveAndFlush(trace);
    }

    private void saveDependencyTrace() {
        LocalDateTime startTime = referenceNow.minusSeconds(40);
        TraceEntity trace = trace("g-slow-1", "gateway", "slow", 800, "ERROR", startTime);
        trace.addSpan(span("gateway-root", "g-slow-1", null, "gateway", "slow", 800, "ERROR", startTime));
        trace.addSpan(span("worker-child", "g-slow-1", "gateway-root", "worker", "worker-job", 400, "ERROR", startTime.plusNanos(50_000_000)));
        traceRepository.saveAndFlush(trace);
    }

    private TraceEntity trace(
            String traceId,
            String serviceName,
            String operation,
            long duration,
            String status,
            LocalDateTime startTime
    ) {
        TraceEntity trace = new TraceEntity(
                traceId,
                serviceName,
                operation,
                startTime,
                startTime.plusNanos(duration * 1_000_000),
                duration,
                status,
                status.equals("ERROR") ? "observed error" : null,
                "GET",
                "/" + operation
        );
        trace.setRootSpanId("span-" + traceId);
        return trace;
    }

    private SpanEntity span(
            String spanId,
            String traceId,
            String parentSpanId,
            String serviceName,
            String operation,
            long duration,
            String status,
            LocalDateTime startTime
    ) {
        return new SpanEntity(
                spanId,
                traceId,
                parentSpanId,
                operation,
                "SERVER",
                startTime,
                startTime.plusNanos(duration * 1_000_000),
                duration,
                status,
                status.equals("ERROR") ? "observed error" : null,
                serviceName
        );
    }
}
