package com.argusiq.tracing;

import com.argusiq.AbstractPostgresIntegrationTest;
import com.argusiq.tracing.entity.SpanEntity;
import com.argusiq.tracing.entity.TraceEntity;
import com.argusiq.tracing.repository.SpanRepository;
import com.argusiq.tracing.repository.TraceRepository;
import com.argusiq.tracing.service.OtlpIngestionService;
import com.google.protobuf.ByteString;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.resource.v1.Resource;
import io.opentelemetry.proto.trace.v1.ResourceSpans;
import io.opentelemetry.proto.trace.v1.ScopeSpans;
import io.opentelemetry.proto.trace.v1.Span;
import io.opentelemetry.proto.trace.v1.Status;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Import(PostgresPersistenceIntegrationTest.RaceTestConfiguration.class)
class PostgresPersistenceIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final byte[] TRACE_ID_BYTES = HexFormat.of().parseHex("102030405060708090a0b0c0d0e0f000");
    private static final String TRACE_ID = HexFormat.of().formatHex(TRACE_ID_BYTES);
    private static final byte[] RACE_TRACE_ID_BYTES = HexFormat.of().parseHex("ffeeddccbbaa99887766554433221100");
    private static final String RACE_TRACE_ID = HexFormat.of().formatHex(RACE_TRACE_ID_BYTES);
    private static final long BASE_NANOS = 1_700_000_000_000_000_000L;

    @Autowired
    private OtlpIngestionService ingestionService;

    @Autowired
    private TraceRepository traceRepository;

    @Autowired
    private SpanRepository spanRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private RaceRepositoryBarrier raceRepositoryBarrier;

    private final List<ExecutorService> executors = new ArrayList<>();

    @BeforeEach
    void cleanDatabase() {
        raceRepositoryBarrier.disarm();
        jdbcTemplate.execute("TRUNCATE TABLE alert_rules, alerts, saved_searches, services, spans, traces RESTART IDENTITY CASCADE");
    }

    @AfterEach
    void stopExecutors() {
        executors.forEach(ExecutorService::shutdownNow);
        executors.clear();
    }

    @Test
    void freshPostgresRunsFlywayThenHibernateValidationAndStartsContext() {
        Integer successfulMigrations = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version IN ('1', '2') AND success",
                Integer.class
        );
        Integer tableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name IN "
                        + "('traces', 'spans', 'services', 'saved_searches', 'alerts', 'alert_rules')",
                Integer.class
        );
        String databaseProduct = jdbcTemplate.queryForObject("SELECT version()", String.class);

        assertEquals(2, successfulMigrations);
        assertEquals(6, tableCount);
        assertNotNull(ingestionService);
        assertTrue(databaseProduct.startsWith("PostgreSQL 17.6"));
    }

    @Test
    void unmanagedNonEmptyLegacySchemaFailsInsteadOfBeingSilentlyBaselined() {
        jdbcTemplate.execute("DROP SCHEMA IF EXISTS legacy_guard CASCADE");
        jdbcTemplate.execute("CREATE SCHEMA legacy_guard");
        jdbcTemplate.execute("CREATE TABLE legacy_guard.existing_argusiq_data (id bigint PRIMARY KEY)");
        jdbcTemplate.update("INSERT INTO legacy_guard.existing_argusiq_data (id) VALUES (1)");
        try {
            Flyway legacyFlyway = Flyway.configure()
                    .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                    .schemas("legacy_guard")
                    .locations("classpath:db/migration")
                    .baselineOnMigrate(false)
                    .load();

            FlywayException failure = assertThrows(FlywayException.class, legacyFlyway::migrate);
            assertTrue(failure.getMessage().contains("non-empty schema"));
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM legacy_guard.existing_argusiq_data", Integer.class));
        } finally {
            jdbcTemplate.execute("DROP SCHEMA legacy_guard CASCADE");
        }
    }

    @Test
    void postgresSchemaMatchesIdentityNullabilityTypesForeignKeyAndDeleteBehavior() {
        String traceIdentity = jdbcTemplate.queryForObject(
                "SELECT identity_generation FROM information_schema.columns "
                        + "WHERE table_schema = 'public' AND table_name = 'traces' AND column_name = 'id'",
                String.class
        );
        String timestampType = jdbcTemplate.queryForObject(
                "SELECT data_type FROM information_schema.columns "
                        + "WHERE table_schema = 'public' AND table_name = 'traces' AND column_name = 'start_time'",
                String.class
        );
        String spanOwnerNullable = jdbcTemplate.queryForObject(
                "SELECT is_nullable FROM information_schema.columns "
                        + "WHERE table_schema = 'public' AND table_name = 'spans' AND column_name = 'trace_entity_id'",
                String.class
        );
        String deleteRule = jdbcTemplate.queryForObject(
                "SELECT delete_rule FROM information_schema.referential_constraints "
                        + "WHERE constraint_schema = 'public' AND constraint_name = 'fk_spans_trace_entity'",
                String.class
        );
        Integer traceMetadataColumns = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = 'public' AND table_name = 'traces' "
                        + "AND column_name IN ('environment', 'service_version', 'sdk_language') "
                        + "AND data_type = 'character varying' AND is_nullable = 'YES'",
                Integer.class
        );

        assertEquals("BY DEFAULT", traceIdentity);
        assertEquals("timestamp without time zone", timestampType);
        assertEquals("YES", spanOwnerNullable);
        assertEquals("NO ACTION", deleteRule);
        assertEquals(3, traceMetadataColumns);

        ingest(span(TRACE_ID_BYTES, 1, null, Span.SpanKind.SPAN_KIND_SERVER, 0, 1_000, "root"));
        traceRepository.deleteById(loadTrace(TRACE_ID).getId());
        assertEquals(0, spanRepository.countByTraceId(TRACE_ID));
    }

    @Test
    void postgresEnforcesTraceAndSpanIdentityAndRollsBackFailedInsertState() {
        LocalDateTime start = LocalDateTime.ofEpochSecond(BASE_NANOS / 1_000_000_000L, 0, ZoneOffset.UTC);
        TraceEntity first = entityTrace(TRACE_ID, start, 1, "first");
        traceRepository.saveAndFlush(first);

        TraceEntity duplicateTrace = entityTrace(TRACE_ID, start, 2, "duplicate-trace");
        assertThrows(DataIntegrityViolationException.class, () -> traceRepository.saveAndFlush(duplicateTrace));
        assertEquals(1, traceRepository.count());
        assertEquals(1, spanRepository.countByTraceId(TRACE_ID));

        TraceEntity duplicateSpanTrace = entityTrace(RACE_TRACE_ID, start, 10, "root");
        duplicateSpanTrace.addSpan(entitySpan(RACE_TRACE_ID, 10, null, start, start.plusSeconds(1), "duplicate", "SERVER"));
        assertThrows(DataIntegrityViolationException.class, () -> traceRepository.saveAndFlush(duplicateSpanTrace));

        assertFalse(traceRepository.findFirstByTraceId(RACE_TRACE_ID).isPresent());
        assertEquals(0, spanRepository.countByTraceId(RACE_TRACE_ID));
        assertEquals(1, traceRepository.count());
    }

    @Test
    void incrementalBatchesAndDuplicateExporterRetryProduceSpanUnionIdempotently() {
        ingest(
                span(TRACE_ID_BYTES, 1, null, Span.SpanKind.SPAN_KIND_SERVER, 0, 1_000, "root"),
                span(TRACE_ID_BYTES, 2, 1L, Span.SpanKind.SPAN_KIND_INTERNAL, 100, 300, "validate"));
        byte[] retryPayload = payload(
                span(TRACE_ID_BYTES, 3, 1L, Span.SpanKind.SPAN_KIND_CLIENT, 350, 700, "database"),
                span(TRACE_ID_BYTES, 4, 3L, Span.SpanKind.SPAN_KIND_INTERNAL, 400, 600, "decode"));

        ingest(retryPayload);
        ingest(retryPayload);

        TraceEntity trace = loadTrace(TRACE_ID);
        assertEquals(1, traceRepository.count());
        assertEquals(4, trace.getSpans().size());
        assertEquals(4, spanRepository.countByTraceId(TRACE_ID));
        assertEquals(1_000L, trace.getDurationMs());
        assertEquals("test", trace.getEnvironment());
    }

    @Test
    void lateRootArrivalRecomputesTheCanonicalSummary() {
        ingest(
                span(TRACE_ID_BYTES, 2, 1L, Span.SpanKind.SPAN_KIND_INTERNAL, 100, 900, "child-before-root"));
        ingest(
                span(TRACE_ID_BYTES, 1, null, Span.SpanKind.SPAN_KIND_SERVER, 0, 1_000, "canonical-root"));

        TraceEntity trace = loadTrace(TRACE_ID);
        assertEquals(spanId(1), trace.getRootSpanId());
        assertEquals("canonical-root", trace.getRootSpanName());
        assertEquals("postgres-integrity-service", trace.getServiceName());
        assertEquals(1_000L, trace.getDurationMs());
        assertEquals("2 spans across 1 services in 1000ms", trace.getTimelineSummary());
        assertEquals(2, trace.getSpans().size());
    }

    @Test
    void concurrentWritesToExistingTraceWaitForPostgresRowLockAndPreserveAllSpans() throws Exception {
        ingest(
                span(TRACE_ID_BYTES, 1, null, Span.SpanKind.SPAN_KIND_SERVER, 0, 1_000, "root"),
                span(TRACE_ID_BYTES, 2, 1L, Span.SpanKind.SPAN_KIND_INTERNAL, 100, 250, "already-persisted"));

        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        ExecutorService lockExecutor = executor(1);
        Future<?> lockHolder = lockExecutor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbcTemplate.queryForObject("SELECT id FROM traces WHERE trace_id = ? FOR UPDATE", Long.class, TRACE_ID);
            lockAcquired.countDown();
            await(releaseLock);
        }));
        assertTrue(lockAcquired.await(5, TimeUnit.SECONDS));

        byte[] firstPayload = payload(
                span(TRACE_ID_BYTES, 3, 1L, Span.SpanKind.SPAN_KIND_CLIENT, 300, 500, "first-concurrent"));
        byte[] secondPayload = payload(
                span(TRACE_ID_BYTES, 4, 1L, Span.SpanKind.SPAN_KIND_CLIENT, 550, 800, "second-concurrent"));
        CyclicBarrier callersReady = new CyclicBarrier(3);
        ExecutorService writers = executor(2);
        Future<?> firstWriter = writers.submit(() -> ingestAfterBarrier(firstPayload, callersReady));
        Future<?> secondWriter = writers.submit(() -> ingestAfterBarrier(secondPayload, callersReady));
        callersReady.await(5, TimeUnit.SECONDS);

        String waitingLockSql = awaitWaitingTraceLockSql();
        assertTrue(waitingLockSql.toLowerCase().contains("for no key update")
                || waitingLockSql.toLowerCase().contains("for update"));

        releaseLock.countDown();
        lockHolder.get(10, TimeUnit.SECONDS);
        firstWriter.get(10, TimeUnit.SECONDS);
        secondWriter.get(10, TimeUnit.SECONDS);

        TraceEntity trace = loadTrace(TRACE_ID);
        assertEquals(4, trace.getSpans().size());
        assertEquals(4, spanRepository.countByTraceId(TRACE_ID));
        assertEquals(Set.of(spanId(1), spanId(2), spanId(3), spanId(4)),
                trace.getSpans().stream().map(SpanEntity::getSpanId).collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void concurrentCreationOfSameTraceRetriesInFreshTransactionAndConverges() throws Exception {
        CyclicBarrier bothObservedMissingTrace = new CyclicBarrier(2);
        raceRepositoryBarrier.arm(bothObservedMissingTrace);

        byte[] firstPayload = payload(
                span(RACE_TRACE_ID_BYTES, 11, null, Span.SpanKind.SPAN_KIND_SERVER, 0, 1_000, "race-root"),
                span(RACE_TRACE_ID_BYTES, 12, 11L, Span.SpanKind.SPAN_KIND_INTERNAL, 100, 250, "first-race"));
        byte[] secondPayload = payload(
                span(RACE_TRACE_ID_BYTES, 13, 11L, Span.SpanKind.SPAN_KIND_CLIENT, 300, 700, "second-race"));
        CyclicBarrier callersReady = new CyclicBarrier(3);
        ExecutorService writers = executor(2);
        Future<?> firstWriter = writers.submit(() -> ingestAfterBarrier(firstPayload, callersReady));
        Future<?> secondWriter = writers.submit(() -> ingestAfterBarrier(secondPayload, callersReady));
        callersReady.await(5, TimeUnit.SECONDS);
        firstWriter.get(15, TimeUnit.SECONDS);
        secondWriter.get(15, TimeUnit.SECONDS);

        TraceEntity trace = loadTrace(RACE_TRACE_ID);
        assertEquals(1, traceRepository.count());
        assertEquals(3, trace.getSpans().size());
        assertEquals(3, spanRepository.countByTraceId(RACE_TRACE_ID));
        assertTrue(raceRepositoryBarrier.attempts() >= 3, "The losing create transaction must retry");
        assertEquals(raceRepositoryBarrier.attempts(), raceRepositoryBarrier.transactionIds().size(),
                "Every merge attempt must use a fresh transaction");
        assertNotEquals(0L, raceRepositoryBarrier.transactionIds().iterator().next());
    }

    private String awaitWaitingTraceLockSql() {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            List<String> waiting = jdbcTemplate.queryForList(
                    "SELECT query FROM pg_stat_activity "
                            + "WHERE datname = current_database() AND pid <> pg_backend_pid() "
                            + "AND wait_event_type = 'Lock' AND lower(query) LIKE '%traces%'",
                    String.class
            );
            if (!waiting.isEmpty()) {
                return waiting.getFirst();
            }
            try {
                Thread.sleep(25);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
        }
        throw new AssertionError("No PostgreSQL lock wait was observed for the pessimistic trace query");
    }

    private TraceEntity entityTrace(String traceId, LocalDateTime start, long rootId, String name) {
        TraceEntity trace = new TraceEntity(traceId, "postgres-integrity-service", name, start, start.plusSeconds(1),
                1_000L, "OK", null, "GET", "/integrity");
        trace.setRootSpanId(spanId(rootId));
        trace.addSpan(entitySpan(traceId, rootId, null, start, start.plusSeconds(1), name, "SERVER"));
        return trace;
    }

    private SpanEntity entitySpan(String traceId, long id, Long parentId, LocalDateTime start, LocalDateTime end,
                                  String name, String kind) {
        return new SpanEntity(spanId(id), traceId, parentId != null ? spanId(parentId) : null, name, kind, start, end,
                Math.max(0L, Duration.between(start, end).toMillis()), "OK", null, "postgres-integrity-service");
    }

    private void ingest(Span... spans) {
        ingest(payload(spans));
    }

    private void ingest(byte[] payload) {
        try {
            ingestionService.ingestProtobufTraces(payload);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void ingestAfterBarrier(byte[] payload, CyclicBarrier barrier) {
        try {
            barrier.await(5, TimeUnit.SECONDS);
            ingestionService.ingestProtobufTraces(payload);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private byte[] payload(Span... spans) {
        Resource resource = Resource.newBuilder()
                .addAttributes(attribute("service.name", "postgres-integrity-service"))
                .addAttributes(attribute("deployment.environment", "test"))
                .build();
        ScopeSpans scopeSpans = ScopeSpans.newBuilder().addAllSpans(List.of(spans)).build();
        return ExportTraceServiceRequest.newBuilder()
                .addResourceSpans(ResourceSpans.newBuilder().setResource(resource).addScopeSpans(scopeSpans))
                .build()
                .toByteArray();
    }

    private Span span(byte[] traceId, long id, Long parentId, Span.SpanKind kind, long startMs, long endMs, String name) {
        Span.Builder builder = Span.newBuilder()
                .setTraceId(ByteString.copyFrom(traceId))
                .setSpanId(ByteString.copyFrom(spanIdBytes(id)))
                .setName(name)
                .setKind(kind)
                .setStartTimeUnixNano(BASE_NANOS + startMs * 1_000_000L)
                .setEndTimeUnixNano(BASE_NANOS + endMs * 1_000_000L)
                .setStatus(Status.newBuilder().setCode(Status.StatusCode.STATUS_CODE_OK));
        if (parentId != null) {
            builder.setParentSpanId(ByteString.copyFrom(spanIdBytes(parentId)));
        } else {
            builder.addAttributes(attribute("http.method", "GET"));
            builder.addAttributes(attribute("http.target", "/integrity"));
        }
        return builder.build();
    }

    private TraceEntity loadTrace(String traceId) {
        TraceEntity trace = traceRepository.findByTraceIdWithSpans(traceId).orElseThrow();
        assertNotNull(trace.getId());
        return trace;
    }

    private ExecutorService executor(int threads) {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        executors.add(executor);
        return executor;
    }

    private void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(10, TimeUnit.SECONDS));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private KeyValue attribute(String key, String value) {
        return KeyValue.newBuilder().setKey(key)
                .setValue(AnyValue.newBuilder().setStringValue(value))
                .build();
    }

    private byte[] spanIdBytes(long id) {
        return ByteBuffer.allocate(Long.BYTES).putLong(id).array();
    }

    private String spanId(long id) {
        return HexFormat.of().formatHex(spanIdBytes(id));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class RaceTestConfiguration {

        @Bean
        RaceRepositoryBarrier raceRepositoryBarrier(JdbcTemplate jdbcTemplate) {
            return new RaceRepositoryBarrier(jdbcTemplate);
        }
    }

    @Aspect
    static class RaceRepositoryBarrier {

        private final JdbcTemplate jdbcTemplate;
        private final AtomicInteger attempts = new AtomicInteger();
        private final Set<Long> transactionIds = ConcurrentHashMap.newKeySet();
        private volatile CyclicBarrier barrier;

        RaceRepositoryBarrier(JdbcTemplate jdbcTemplate) {
            this.jdbcTemplate = jdbcTemplate;
        }

        void arm(CyclicBarrier barrier) {
            attempts.set(0);
            transactionIds.clear();
            this.barrier = barrier;
        }

        void disarm() {
            barrier = null;
        }

        int attempts() {
            return attempts.get();
        }

        Set<Long> transactionIds() {
            return Set.copyOf(transactionIds);
        }

        @Around("execution(* com.argusiq.tracing.repository.TraceRepository.findByTraceIdForUpdate(..)) && args(traceId)")
        Object afterRealLookup(ProceedingJoinPoint joinPoint, String traceId) throws Throwable {
            Object result = joinPoint.proceed();
            CyclicBarrier currentBarrier = barrier;
            if (currentBarrier == null || !RACE_TRACE_ID.equals(traceId)) {
                return result;
            }

            int attempt = attempts.incrementAndGet();
            transactionIds.add(jdbcTemplate.queryForObject("SELECT txid_current()", Long.class));
            if (attempt <= 2 && result instanceof Optional<?> optional && optional.isEmpty()) {
                currentBarrier.await(5, TimeUnit.SECONDS);
            }
            return result;
        }
    }
}
