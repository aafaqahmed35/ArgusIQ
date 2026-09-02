package com.argusiq.tracing;

import com.argusiq.AbstractPostgresIntegrationTest;
import com.argusiq.tracing.dto.PageResponse;
import com.argusiq.tracing.dto.TraceResponseDto;
import com.argusiq.tracing.dto.TraceSearchCriteria;
import com.argusiq.tracing.entity.SpanEntity;
import com.argusiq.tracing.entity.TraceEntity;
import com.argusiq.tracing.repository.TraceRepository;
import com.argusiq.tracing.service.SearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TraceSearchPostgresIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 1, 15, 12, 0);

    @Autowired
    private TraceRepository traceRepository;

    @Autowired
    private SearchService searchService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE spans, traces RESTART IDENTITY CASCADE");
        saveTrace("trace-health", "gateway", "GET", "/health", "OK", 25, BASE_TIME.plusMinutes(1), "health check");
        saveTrace("trace-orders-ok", "orders", "GET", "/api/orders", "OK", 120, BASE_TIME.plusMinutes(2), "list orders");
        saveTrace("trace-orders-error", "orders", "POST", "/api/orders", "ERROR", 850, BASE_TIME.plusMinutes(3), "create order");
        saveTrace("trace-customer", "customer", "GET", "/api/customers/7", "OK", 310, BASE_TIME.plusMinutes(4), "customer lookup");
        saveTrace("trace-unset", "gateway", "PATCH", "/api/settings", "UNSET", 1_250, BASE_TIME.plusMinutes(5), "update settings");
        saveTrace("trace-old-error", "gateway", "DELETE", "/api/orders/9", "ERROR", 510, BASE_TIME.plusMinutes(6), "delete order");
        saveTrace("trace-tie-a", "gateway", "GET", "/api/tie/a", "OK", 75, BASE_TIME.plusMinutes(10), "tie operation a");
        saveTrace("trace-tie-b", "gateway", "GET", "/api/tie/b", "OK", 75, BASE_TIME.plusMinutes(10), "tie operation b");
    }

    @Test
    void defaultPaginationIsNewestFirst() {
        TraceSearchCriteria criteria = criteria(0, 3);

        PageResponse<TraceResponseDto> page = searchService.searchTraces(criteria);

        assertEquals(List.of("trace-tie-b", "trace-tie-a", "trace-old-error"), traceIds(page));
    }

    @Test
    void equalPrimarySortValuesUseIdAsDeterministicTieBreaker() {
        TraceSearchCriteria criteria = criteria(0, 10);
        criteria.setSortBy("durationMs");
        criteria.setSortDirection("asc");

        List<String> ids = traceIds(searchService.searchTraces(criteria));

        assertTrue(ids.indexOf("trace-tie-a") < ids.indexOf("trace-tie-b"));
    }

    @Test
    void pageBoundariesReturnOnlyTheRequestedSlice() {
        PageResponse<TraceResponseDto> page = searchService.searchTraces(criteria(2, 3));

        assertEquals(2, page.getItems().size());
        assertTrue(page.isHasPrevious());
        assertFalse(page.isHasNext());
    }

    @Test
    void paginationMetadataReportsCorrectTotals() throws Exception {
        mockMvc.perform(get("/api/v1/search/traces")
                        .param("size", "3")
                        .with(httpBasic("postgres-investigator", INVESTIGATION_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(3))
                .andExpect(jsonPath("$.totalItems").value(8))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.hasPrevious").value(false))
                .andExpect(jsonPath("$.content").doesNotExist());
    }

    @Test
    void maximumPageSizeIsEnforced() throws Exception {
        mockMvc.perform(get("/api/v1/search/traces")
                        .param("size", "101")
                        .with(httpBasic("postgres-investigator", INVESTIGATION_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid trace search criteria"));
    }

    @Test
    void invalidPageAndSizeAreRejected() throws Exception {
        mockMvc.perform(get("/api/v1/search/traces")
                        .param("page", "-1")
                        .with(httpBasic("postgres-investigator", INVESTIGATION_PASSWORD)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/search/traces")
                        .param("size", "0")
                        .with(httpBasic("postgres-investigator", INVESTIGATION_PASSWORD)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void serviceFilterSupportsIndexedExactAndLegacyPartialMatching() {
        TraceSearchCriteria criteria = new TraceSearchCriteria();
        criteria.setServiceExact("ORDERS");

        PageResponse<TraceResponseDto> page = searchService.searchTraces(criteria);

        assertEquals(2, page.getTotalItems());
        assertTrue(page.getItems().stream().allMatch(trace -> "orders".equals(trace.getServiceName())));

        TraceSearchCriteria partialCriteria = new TraceSearchCriteria();
        partialCriteria.setService("rde");
        assertEquals(2, searchService.searchTraces(partialCriteria).getTotalItems());
    }

    @Test
    void statusFilterIsCaseInsensitiveAndExact() {
        TraceSearchCriteria criteria = new TraceSearchCriteria();
        criteria.setStatus("error");

        PageResponse<TraceResponseDto> page = searchService.searchTraces(criteria);

        assertEquals(2, page.getTotalItems());
        assertTrue(page.getItems().stream().allMatch(trace -> "ERROR".equals(trace.getStatusCode())));
    }

    @Test
    void endpointFilterUsesCaseInsensitiveContainsMatching() {
        TraceSearchCriteria criteria = new TraceSearchCriteria();
        criteria.setEndpoint("ORDERS");

        assertEquals(3, searchService.searchTraces(criteria).getTotalItems());
    }

    @Test
    void httpMethodFilterIsCaseInsensitiveAndExact() {
        TraceSearchCriteria criteria = new TraceSearchCriteria();
        criteria.setHttpMethod("get");

        assertEquals(5, searchService.searchTraces(criteria).getTotalItems());
    }

    @Test
    void durationRangeUsesInclusiveBounds() {
        TraceSearchCriteria criteria = new TraceSearchCriteria();
        criteria.setMinDuration(120L);
        criteria.setMaxDuration(510L);

        assertEquals(List.of("trace-old-error", "trace-customer", "trace-orders-ok"), traceIds(searchService.searchTraces(criteria)));
    }

    @Test
    void timeRangeUsesInclusiveBounds() {
        TraceSearchCriteria criteria = new TraceSearchCriteria();
        criteria.setFrom(BASE_TIME.plusMinutes(3));
        criteria.setTo(BASE_TIME.plusMinutes(5));

        assertEquals(3, searchService.searchTraces(criteria).getTotalItems());
    }

    @Test
    void traceIdFilterIsExact() {
        TraceSearchCriteria criteria = new TraceSearchCriteria();
        criteria.setTraceId("trace-orders-ok");
        assertEquals(1, searchService.searchTraces(criteria).getTotalItems());

        criteria.setTraceId("trace-orders");
        assertEquals(0, searchService.searchTraces(criteria).getTotalItems());
    }

    @Test
    void freeTextSearchCoversInvestigationSummaryFields() {
        TraceSearchCriteria criteria = new TraceSearchCriteria();
        criteria.setQuery("CREATE ORDER");

        assertEquals(List.of("trace-orders-error"), traceIds(searchService.searchTraces(criteria)));
    }

    @Test
    void combinedFiltersAreAppliedWithAndSemantics() {
        TraceSearchCriteria criteria = new TraceSearchCriteria();
        criteria.setService("orders");
        criteria.setEndpoint("/api/orders");
        criteria.setHttpMethod("POST");
        criteria.setStatus("ERROR");
        criteria.setMinDuration(800L);

        assertEquals(List.of("trace-orders-error"), traceIds(searchService.searchTraces(criteria)));
    }

    @Test
    void supportedSortingHonorsDirection() {
        TraceSearchCriteria criteria = criteria(0, 8);
        criteria.setSortBy("durationMs");
        criteria.setSortDirection("desc");

        List<Long> durations = searchService.searchTraces(criteria).getItems().stream()
                .map(TraceResponseDto::getDurationMs)
                .toList();
        assertEquals(List.of(1_250L, 850L, 510L, 310L, 120L, 75L, 75L, 25L), durations);
    }

    @Test
    void unsupportedSortIsRejectedInsteadOfSilentlyNormalized() throws Exception {
        mockMvc.perform(get("/api/v1/search/traces")
                        .param("sortBy", "spans.secret")
                        .with(httpBasic("postgres-investigator", INVESTIGATION_PASSWORD)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void blankFiltersDoNotRestrictResults() {
        TraceSearchCriteria criteria = new TraceSearchCriteria();
        criteria.setQuery("  ");
        criteria.setService(" ");
        criteria.setEndpoint("");
        criteria.setTraceId("\t");

        assertEquals(8, searchService.searchTraces(criteria).getTotalItems());
    }

    @Test
    void noResultQueryReturnsAValidEmptyPage() {
        TraceSearchCriteria criteria = new TraceSearchCriteria();
        criteria.setQuery("not-present");

        PageResponse<TraceResponseDto> page = searchService.searchTraces(criteria);

        assertTrue(page.getItems().isEmpty());
        assertEquals(0, page.getTotalItems());
        assertEquals(0, page.getTotalPages());
        assertFalse(page.isHasNext());
        assertFalse(page.isHasPrevious());
    }

    @Test
    void invalidDurationAndTimeRangesAreRejected() {
        TraceSearchCriteria durationCriteria = new TraceSearchCriteria();
        durationCriteria.setMinDuration(500L);
        durationCriteria.setMaxDuration(100L);
        assertThrows(IllegalArgumentException.class, () -> searchService.searchTraces(durationCriteria));

        TraceSearchCriteria timeCriteria = new TraceSearchCriteria();
        timeCriteria.setFrom(BASE_TIME.plusHours(1));
        timeCriteria.setTo(BASE_TIME);
        assertThrows(IllegalArgumentException.class, () -> searchService.searchTraces(timeCriteria));
    }

    @Test
    void representativePostgresQueriesExecuteAgainstDeclaredIndexes() {
        jdbcTemplate.update("""
                INSERT INTO traces (
                    trace_id, service_name, root_span_name, start_time, end_time, duration_ms,
                    status_code, http_method, request_uri, business_operation, entry_endpoint,
                    exit_status, critical_path_duration_ms, evidence_graph_id
                )
                SELECT
                    'bulk-' || value,
                    CASE WHEN value % 2 = 0 THEN 'orders' ELSE 'gateway' END,
                    'GET /api/bulk/' || value,
                    ?::timestamp + (value || ' seconds')::interval,
                    ?::timestamp + (value || ' seconds')::interval,
                    value % 2000,
                    CASE WHEN value % 10 = 0 THEN 'ERROR' ELSE 'OK' END,
                    'GET',
                    '/api/bulk/' || value,
                    'bulk operation',
                    '/api/bulk/' || value,
                    'OK',
                    value % 2000,
                    'trace:bulk-' || value
                FROM generate_series(1, 5000) AS value
                """, BASE_TIME, BASE_TIME);
        jdbcTemplate.execute("ANALYZE traces");

        List<String> indexDefinitions = jdbcTemplate.queryForList(
                "SELECT indexdef FROM pg_indexes WHERE schemaname = 'public' AND tablename = 'traces'",
                String.class
        );
        assertTrue(indexDefinitions.stream().anyMatch(definition -> definition.contains("idx_trace_start_time") && definition.contains("start_time DESC") && definition.contains("id DESC")));
        assertTrue(indexDefinitions.stream().anyMatch(definition -> definition.contains("idx_trace_service_start_time") && definition.contains("lower((service_name)::text)")));
        assertTrue(indexDefinitions.stream().anyMatch(definition -> definition.contains("idx_trace_status_start_time")));
        assertTrue(indexDefinitions.stream().anyMatch(definition -> definition.contains("idx_trace_method_start_time")));
        assertTrue(indexDefinitions.stream().anyMatch(definition -> definition.contains("idx_trace_duration_start_time")));

        assertPlanExecutes("EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM traces ORDER BY start_time DESC, id DESC LIMIT 25");
        assertPlanExecutes("EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM traces WHERE lower(service_name) = 'orders' ORDER BY start_time DESC, id DESC LIMIT 25");
        assertPlanExecutes("EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM traces WHERE lower(status_code) = 'error' ORDER BY start_time DESC, id DESC LIMIT 25");
        assertPlanExecutes("EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM traces WHERE lower(request_uri) LIKE '%bulk%' ORDER BY start_time DESC, id DESC LIMIT 25");
        assertPlanExecutes("EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM traces WHERE duration_ms >= 1000 ORDER BY start_time DESC, id DESC LIMIT 25");
        assertPlanExecutes("EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM traces WHERE start_time >= TIMESTAMP '2026-01-15 12:30:00' ORDER BY start_time DESC, id DESC LIMIT 25");
        assertPlanExecutes("EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM traces WHERE trace_id = 'bulk-4000'");
    }

    private TraceSearchCriteria criteria(int page, int size) {
        TraceSearchCriteria criteria = new TraceSearchCriteria();
        criteria.setPage(page);
        criteria.setSize(size);
        return criteria;
    }

    private List<String> traceIds(PageResponse<TraceResponseDto> page) {
        return page.getItems().stream().map(TraceResponseDto::getTraceId).toList();
    }

    private void saveTrace(
            String traceId,
            String service,
            String method,
            String endpoint,
            String status,
            long duration,
            LocalDateTime startTime,
            String operation
    ) {
        TraceEntity trace = new TraceEntity(
                traceId,
                service,
                operation,
                startTime,
                startTime.plusNanos(duration * 1_000_000),
                duration,
                status,
                null,
                method,
                endpoint
        );
        trace.setBusinessOperation(operation);
        trace.setEntryEndpoint(endpoint);
        trace.setExitStatus(status);
        trace.setCriticalPathDurationMs(duration);
        trace.setEvidenceGraphId("trace:" + traceId);

        SpanEntity span = new SpanEntity(
                "span-" + traceId,
                traceId,
                null,
                operation,
                "SERVER",
                startTime,
                startTime.plusNanos(duration * 1_000_000),
                duration,
                status,
                null,
                service
        );
        span.setHttpMethod(method);
        span.setHttpStatusCode("ERROR".equals(status) ? 500 : 200);
        trace.addSpan(span);
        traceRepository.saveAndFlush(trace);
    }

    private void assertPlanExecutes(String sql) {
        List<String> plan = jdbcTemplate.queryForList(sql, String.class);
        assertFalse(plan.isEmpty());
        assertTrue(plan.stream().anyMatch(line -> line.contains("Execution Time")));
    }
}
