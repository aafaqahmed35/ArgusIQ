package com.argusiq.tracing;

import com.argusiq.tracing.dto.AlertRequest;
import com.argusiq.tracing.dto.AlertResponse;
import com.argusiq.tracing.dto.AlertRuleRequest;
import com.argusiq.tracing.dto.MetricsResponse;
import com.argusiq.tracing.dto.PageResponse;
import com.argusiq.tracing.dto.SavedSearchRequest;
import com.argusiq.tracing.dto.ServiceResponse;
import com.argusiq.tracing.dto.TraceResponseDto;
import com.argusiq.tracing.dto.TraceSearchCriteria;
import com.argusiq.tracing.entity.MonitoredService;
import com.argusiq.tracing.entity.SpanEntity;
import com.argusiq.tracing.entity.TraceEntity;
import com.argusiq.tracing.repository.AlertRepository;
import com.argusiq.tracing.repository.AlertRuleRepository;
import com.argusiq.tracing.repository.MonitoredServiceRepository;
import com.argusiq.tracing.repository.SavedSearchRepository;
import com.argusiq.tracing.repository.TraceRepository;
import com.argusiq.tracing.service.AlertService;
import com.argusiq.tracing.service.MetricsService;
import com.argusiq.tracing.service.SearchService;
import com.argusiq.tracing.service.ServicesBackendService;
import com.argusiq.tracing.service.TraceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class Phase2BackendServiceIntegrationTest {

    @Autowired
    private TraceRepository traceRepository;

    @Autowired
    private MonitoredServiceRepository serviceRepository;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private AlertRuleRepository alertRuleRepository;

    @Autowired
    private SavedSearchRepository savedSearchRepository;

    @Autowired
    private MetricsService metricsService;

    @Autowired
    private SearchService searchService;

    @Autowired
    private AlertService alertService;

    @Autowired
    private ServicesBackendService servicesBackendService;

    @Autowired
    private TraceService traceService;

    @BeforeEach
    void setup() {
        alertRuleRepository.deleteAll();
        alertRepository.deleteAll();
        savedSearchRepository.deleteAll();
        traceRepository.deleteAll();
        serviceRepository.deleteAll();
        seedTelemetry();
        metricsService.invalidate();
    }

    @Test
    void metricsComputesLatencyPercentilesAndRates() {
        MetricsResponse metrics = metricsService.getMetrics();

        assertEquals(3, metrics.getThroughput());
        assertEquals(250.0, metrics.getAverageLatencyMs());
        assertEquals(200.0, metrics.getMedianLatencyMs());
        assertTrue(metrics.getP95LatencyMs() > 500.0);
        assertEquals(50, metrics.getMinimumLatencyMs());
        assertEquals(500, metrics.getMaximumLatencyMs());
        assertEquals(100.0 / 3.0, metrics.getErrorRate(), 0.01);
        assertEquals(2, metrics.getUniqueEndpoints());
        assertEquals(2, metrics.getUniqueServices());
        assertFalse(metrics.getStatusCodeDistribution().isEmpty());
    }

    @Test
    void searchCombinesEndpointDurationStatusAndBusinessFields() {
        TraceSearchCriteria criteria = new TraceSearchCriteria();
        criteria.setEndpoint("/customer");
        criteria.setHttpMethod("POST");
        criteria.setMinDuration(400L);
        criteria.setStatus("ERROR");
        criteria.setCustomerId("customer-7");

        PageResponse<TraceResponseDto> result = searchService.searchTraces(criteria);

        assertEquals(1, result.getTotalElements());
        assertEquals("trace-error", result.getContent().get(0).getTraceId());
    }

    @Test
    void searchSupportsSortingAndPagination() {
        TraceSearchCriteria criteria = new TraceSearchCriteria();
        criteria.setSortBy("durationMs");
        criteria.setSortDirection("asc");
        criteria.setPage(0);
        criteria.setSize(2);

        PageResponse<TraceResponseDto> result = searchService.searchTraces(criteria);

        assertEquals(3, result.getTotalElements());
        assertEquals(2, result.getContent().size());
        assertEquals(50L, result.getContent().get(0).getDurationMs());
    }

    @Test
    void savedSearchPersistsFiltersAndLastUsedTimestamp() {
        SavedSearchRequest request = new SavedSearchRequest();
        request.setName("Slow customer errors");
        request.setFilters(Map.of("endpoint", "/customer", "minDuration", 400));

        Long id = searchService.saveSearch(request).getId();

        assertEquals(1, searchService.getSavedSearches().size());
        assertNotNull(searchService.markSavedSearchUsed(id).getLastUsedAt());
    }

    @Test
    void servicesExposeAnalyticsAndRecentActivity() {
        ServiceResponse service = servicesBackendService.getServices().stream()
                .filter(item -> "gateway".equals(item.getServiceName()))
                .findFirst()
                .orElseThrow();

        assertEquals(2, service.getRequestVolume());
        assertTrue(service.getErrorRate() > 0.0);
        assertFalse(service.getTopEndpoints().isEmpty());
        assertTrue(servicesBackendService.getService(service.getId()).orElseThrow().getRecentTraces().size() >= 1);
    }

    @Test
    void dependencyGraphIsInferredFromCrossServiceParentChildSpans() {
        assertTrue(servicesBackendService.getDependencyGraph().getNodes().contains("gateway"));
        assertTrue(servicesBackendService.getDependencyGraph().getEdges().stream()
                .anyMatch(edge -> "gateway".equals(edge.getSource()) && "customer-service".equals(edge.getTarget())));
    }

    @Test
    void alertsSupportCrudAndResolutionState() {
        AlertRequest request = new AlertRequest();
        request.setType("HIGH_LATENCY");
        request.setSeverity("CRITICAL");
        request.setTitle("Customer endpoint slow");
        request.setRelatedTrace("trace-error");
        request.setRelatedService("gateway");

        AlertResponse created = alertService.createAlert(request);
        assertEquals("OPEN", created.getStatus());

        AlertRequest update = new AlertRequest();
        update.setStatus("RESOLVED");
        update.setAcknowledged(true);
        AlertResponse resolved = alertService.updateAlert(created.getAlertId(), update).orElseThrow();
        assertTrue(resolved.isAcknowledged());
        assertNotNull(resolved.getResolvedTime());
        assertTrue(alertService.deleteAlert(created.getAlertId()));
    }

    @Test
    void alertRulesPersistSchedulerInputs() {
        AlertRuleRequest request = new AlertRuleRequest();
        request.setType("ERROR_RATE_SPIKE");
        request.setThreshold(5.0);
        request.setWindowSeconds(600L);
        request.setComparator("GREATER_THAN");
        request.setEnabled(true);

        assertEquals("ERROR_RATE_SPIKE", alertService.createRule(request).getType());
        assertEquals(1, alertService.getRules().size());
    }

    @Test
    void httpRequestTraceCreatesOneTraceWithOneRootServerSpan() {
        long before = traceRepository.count();
        TraceEntity trace = traceService.saveHttpRequestTrace("GET", "/api/v1/orders", 25L, LocalDateTime.now(ZoneOffset.UTC), 200);

        assertEquals(before + 1, traceRepository.count());
        assertEquals(1, trace.getSpans().size());
        assertEquals("SERVER", trace.getSpans().get(0).getKind());
        assertEquals(trace.getRootSpanId(), trace.getSpans().get(0).getSpanId());
    }

    private void seedTelemetry() {
        serviceRepository.save(new MonitoredService("gateway", "test", "1.0.0", "java", LocalDateTime.now(ZoneOffset.UTC).minusHours(1), LocalDateTime.now(ZoneOffset.UTC), "ACTIVE"));
        serviceRepository.save(new MonitoredService("customer-service", "test", "1.0.0", "java", LocalDateTime.now(ZoneOffset.UTC).minusHours(1), LocalDateTime.now(ZoneOffset.UTC), "ACTIVE"));
        traceRepository.save(trace("trace-fast", "gateway", "GET", "/health", "OK", 50L, "span-fast-root", null));
        traceRepository.save(trace("trace-ok", "gateway", "POST", "/customer", "OK", 200L, "span-ok-root", null));
        TraceEntity error = trace("trace-error", "gateway", "POST", "/customer", "ERROR", 500L, "span-error-root", "customer-7");
        SpanEntity child = new SpanEntity("span-error-child", "trace-error", "span-error-root", "Customer lookup", "CLIENT", LocalDateTime.now(ZoneOffset.UTC).minusMillis(450), LocalDateTime.now(ZoneOffset.UTC), 450L, "ERROR", "timeout", "customer-service");
        child.setCustomerId("customer-7");
        error.addSpan(child);
        traceRepository.save(error);
    }

    private TraceEntity trace(String traceId, String service, String method, String uri, String status, long durationMs, String spanId, String customerId) {
        LocalDateTime end = LocalDateTime.now(ZoneOffset.UTC);
        TraceEntity trace = new TraceEntity(traceId, service, method + " " + uri, end.minusNanos(durationMs * 1_000_000L), end, durationMs, status, null, method, uri);
        trace.setRootSpanId(spanId);
        trace.setBusinessOperation(method + " " + uri);
        trace.setCriticalPathDurationMs(durationMs);
        trace.setEntryEndpoint(uri);
        trace.setExitStatus(status);
        trace.setEvidenceGraphId("trace:" + traceId);
        SpanEntity root = new SpanEntity(spanId, traceId, null, method + " " + uri, "SERVER", end.minusNanos(durationMs * 1_000_000L), end, durationMs, status, null, service);
        root.setHttpMethod(method);
        root.setHttpStatusCode("ERROR".equals(status) ? 500 : 200);
        root.setCustomerId(customerId);
        trace.addSpan(root);
        return trace;
    }
}
