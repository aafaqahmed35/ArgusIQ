package com.argusiq.tracing;

import com.argusiq.AbstractPostgresIntegrationTest;
import com.argusiq.tracing.dto.AlertEvidence;
import com.argusiq.tracing.dto.AlertRuleRequest;
import com.argusiq.tracing.entity.AlertRule;
import com.argusiq.tracing.repository.AlertRepository;
import com.argusiq.tracing.repository.AlertRuleRepository;
import com.argusiq.tracing.repository.TelemetryAnalyticsRepository;
import com.argusiq.tracing.service.AlertEvaluationService;
import com.argusiq.tracing.service.AlertEvaluation;
import com.argusiq.tracing.service.AlertOccurrenceWriter;
import com.argusiq.tracing.service.AlertService;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class AlertEvaluationPostgresIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private AlertRepository alertRepository;
    @Autowired
    private AlertRuleRepository ruleRepository;
    @Autowired
    private AlertService alertService;
    @Autowired
    private AlertOccurrenceWriter occurrenceWriter;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private TelemetryAnalyticsRepository analyticsRepository;

    @BeforeEach
    void setup() {
        alertRepository.deleteAll();
        ruleRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM spans");
    }

    @Test
    void concurrentEvaluationConvergesToOneActiveOccurrence() throws Exception {
        AlertRuleRequest request = new AlertRuleRequest();
        request.setName("Concurrent error rate");
        request.setType(AlertEvaluationService.ERROR_RATE_THRESHOLD);
        request.setSeverity("WARNING");
        request.setThreshold(10.0);
        request.setWindowSeconds(300L);
        request.setMinimumSamples(1L);
        alertService.createRule(request);
        AlertRule rule = ruleRepository.findAll().getFirst();
        LocalDateTime now = LocalDateTime.of(2026, 9, 5, 6, 0);
        var evidence = new AlertEvidence(
                "ERROR_RATE_PERCENT", 20.0, 10.0, "PERCENT", 10L, 2L,
                now.minusMinutes(5), now, null, null, null, null, null, null, null
        );
        var evaluation = new AlertEvaluation(
                now, now.minusMinutes(5), now, "Observed 20.0% error rate.",
                null, null, null, evidence
        );
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                start.await();
                occurrenceWriter.recordMatch(rule.getId(), evaluation);
                return null;
            });
            var second = executor.submit(() -> {
                start.await();
                occurrenceWriter.recordMatch(rule.getId(), evaluation);
                return null;
            });
            start.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        }

        assertEquals(1, alertRepository.count());
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM alerts WHERE active_key IS NOT NULL", Long.class));
        assertTrue(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_name = 'uk_alert_active_key'",
                Long.class) >= 1);
    }

    @Test
    void aggregateUsesClosedServerSpanWindowAndExactServiceIdentity() {
        LocalDateTime end = LocalDateTime.of(2026, 9, 5, 6, 0);
        insertSpan("checkout-ok", "SERVER", "checkout", "OK", 100, end.minusMinutes(4));
        insertSpan("checkout-error", "server", "checkout", "ERROR", 200, end);
        insertSpan("inventory-error", "SERVER", "inventory", "error", 300, end.minusMinutes(2));
        insertSpan("checkout-client", "CLIENT", "checkout", "ERROR", 900, end.minusMinutes(1));
        insertSpan("too-old", "SERVER", "checkout", "ERROR", 1000, end.minusMinutes(5).minusNanos(1_000));

        var scoped = analyticsRepository.alertWindowAggregate(end.minusMinutes(5), end, "checkout");
        assertEquals(2L, scoped.sampleCount());
        assertEquals(1L, scoped.errorCount());
        assertEquals(195.0, scoped.p95LatencyMs(), 0.0001);

        var global = analyticsRepository.alertWindowAggregate(end.minusMinutes(5), end, null);
        assertEquals(3L, global.sampleCount());
        assertEquals(2L, global.errorCount());
        assertEquals(290.0, global.p95LatencyMs(), 0.0001);
    }

    @Test
    void v4MigratesRepresentativeV3RowsWithHonestLegacySemantics() {
        String schema = "alert_v3_upgrade";
        jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        jdbcTemplate.execute("CREATE SCHEMA " + schema);
        String separator = POSTGRES.getJdbcUrl().contains("?") ? "&" : "?";
        String schemaUrl = POSTGRES.getJdbcUrl() + separator + "currentSchema=" + schema;
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                schemaUrl, POSTGRES.getUsername(), POSTGRES.getPassword()
        );

        try {
            Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration", "classpath:db/postgresql")
                    .target(MigrationVersion.fromVersion("3"))
                    .load()
                    .migrate();
            JdbcTemplate upgradeDatabase = new JdbcTemplate(dataSource);
            upgradeDatabase.update("""
                    INSERT INTO alert_rules (enabled, threshold, window_seconds, comparator, type)
                    VALUES (TRUE, 12.5, 300, 'GREATER_THAN_OR_EQUAL', 'LEGACY_PLACEHOLDER')
                    """);
            upgradeDatabase.update("""
                    INSERT INTO alerts (
                        acknowledged, created_time, description, evidence, owner_placeholder,
                        recommendation_placeholder, related_service, related_span, related_trace,
                        severity, status, title, type
                    ) VALUES (
                        FALSE, TIMESTAMP '2026-09-05 06:00:00', 'legacy description', 'legacy evidence',
                        'legacy owner', 'legacy recommendation', 'checkout', 'span-1', 'trace-1',
                        'WARNING', 'OPEN', 'Legacy alert', 'LEGACY_PLACEHOLDER'
                    )
                    """);

            Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration", "classpath:db/postgresql")
                    .load()
                    .migrate();

            assertEquals(1L, upgradeDatabase.queryForObject("SELECT COUNT(*) FROM alert_rules", Long.class));
            assertEquals(Boolean.FALSE, upgradeDatabase.queryForObject(
                    "SELECT enabled FROM alert_rules", Boolean.class
            ));
            assertEquals(12.5, upgradeDatabase.queryForObject(
                    "SELECT threshold FROM alert_rules", Double.class
            ));
            assertEquals(1L, upgradeDatabase.queryForObject("SELECT COUNT(*) FROM alerts", Long.class));
            assertEquals("Legacy alert", upgradeDatabase.queryForObject(
                    "SELECT title FROM alerts", String.class
            ));
            assertEquals("legacy description\nLegacy evidence: legacy evidence", upgradeDatabase.queryForObject(
                    "SELECT description FROM alerts", String.class
            ));
            assertEquals(LocalDateTime.of(2026, 9, 5, 6, 0), upgradeDatabase.queryForObject(
                    "SELECT first_triggered_at FROM alerts", LocalDateTime.class
            ));
            assertEquals(LocalDateTime.of(2026, 9, 5, 6, 0), upgradeDatabase.queryForObject(
                    "SELECT last_triggered_at FROM alerts", LocalDateTime.class
            ));
            assertEquals(LocalDateTime.of(2026, 9, 5, 6, 0), upgradeDatabase.queryForObject(
                    "SELECT evaluation_time FROM alerts", LocalDateTime.class
            ));
            assertEquals("LEGACY_IMPORTED", upgradeDatabase.queryForObject(
                    "SELECT last_evaluation_state FROM alerts", String.class
            ));
            assertEquals(0L, upgradeDatabase.queryForObject("""
                    SELECT COUNT(*) FROM information_schema.columns
                    WHERE table_schema = ? AND table_name = 'alerts'
                      AND column_name IN ('evidence', 'recommendation_placeholder', 'owner_placeholder')
                    """, Long.class, schema));
            assertEquals(1L, upgradeDatabase.queryForObject("""
                    SELECT COUNT(*) FROM flyway_schema_history WHERE version = '4' AND success = TRUE
                    """, Long.class));
        } finally {
            jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    private void insertSpan(String spanId, String kind, String service, String status, long duration, LocalDateTime start) {
        jdbcTemplate.update("""
                INSERT INTO spans (
                    duration_ms, end_time, start_time, kind, status_code, span_id, trace_id, name, service_name
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, duration, start.plusNanos(duration * 1_000_000), start, kind, status,
                spanId, "trace-" + spanId, "operation", service);
    }
}
