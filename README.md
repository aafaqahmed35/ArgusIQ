# ArgusIQ

Real-time backend observability platform built with Spring Boot, React, PostgreSQL, WebSockets, and Docker.

## Architecture

```text
ArgusIQ/
├── backend/     # Spring Boot backend
├── frontend/    # React + Vite frontend
├── docs/        # Architecture and documentation
├── docker/      # Docker configs
├── scripts/     # Helper scripts
```

## Tech Stack

### Backend
- Java 21
- Spring Boot
- Spring WebSocket
- Spring Data JPA
- PostgreSQL

### Frontend
- React
- Vite
- STOMP.js
- SockJS

## Features

- Real-time API trace monitoring
- Live websocket updates
- Request execution tracking
- PostgreSQL persistence
- REST + WebSocket architecture
- Trace filtering infrastructure

## Running Locally

### Required configuration

ArgusIQ reads deployment credentials from environment variables. Do not commit their values or place them in tracked configuration files.

| Variable | Required | Purpose |
| --- | --- | --- |
| `ARGUSIQ_DB_PASSWORD` | Yes | PostgreSQL password |
| `ARGUSIQ_INVESTIGATION_PASSWORD` | Yes | Password for investigation REST and WebSocket access; minimum 16 characters |
| `ARGUSIQ_INGESTION_PASSWORD` | Yes | Password for OTLP ingestion; minimum 16 characters |
| `ARGUSIQ_DB_URL` | No | JDBC URL; defaults to local `argusiq` PostgreSQL |
| `ARGUSIQ_DB_USERNAME` | No | PostgreSQL username; defaults to `argusiq` |
| `ARGUSIQ_INVESTIGATION_USERNAME` | No | Investigation principal; defaults to `investigator` |
| `ARGUSIQ_INGESTION_USERNAME` | No | Collector principal; defaults to `collector` |
| `ARGUSIQ_ALLOWED_ORIGINS` | No | Comma-separated frontend origins; defaults to `http://localhost:5173` |

Supply required values through the shell, IDE run configuration, container secret, or deployment secret manager. The application intentionally fails startup when a required password is absent.

The current boundary uses HTTP Basic with separate investigator and collector roles and must be deployed behind TLS. There is intentionally no login UI yet. For local browser use, open a protected backend URL such as `http://localhost:8080/api/v1/traces` and authenticate with the investigator principal before opening the frontend. Configure the OpenTelemetry collector to use the collector principal when exporting to `/v1/traces`.

The frontend connection targets can be changed with `VITE_ARGUSIQ_API_BASE_URL` and `VITE_ARGUSIQ_WEBSOCKET_URL`. These are public build settings and must never contain credentials.

The database credential previously committed to this repository must be rotated or revoked. Removing it from the current tree does not remove it from Git history; history cleanup is a separate, potentially disruptive operation and has not been performed.

### Backend

```bash
cd backend
./mvnw spring-boot:run
```

On a fresh PostgreSQL database, Flyway applies the migrations in
`backend/src/main/resources/db/migration` before JPA starts. Hibernate runs with
`ddl-auto=validate`; it no longer creates, updates, or repairs application tables.
Normal local startup uses `ARGUSIQ_DB_URL`, `ARGUSIQ_DB_USERNAME`, and
`ARGUSIQ_DB_PASSWORD` from the configuration table above.

#### Telemetry data contract

All persisted `LocalDateTime` telemetry values represent UTC wall-clock time;
PostgreSQL stores them as `timestamp without time zone`. OTLP resource metadata is
snapshotted on each trace so historical trace detail does not change when a
service later reports a different deployment or version. Missing environment,
service version, and SDK language remain `null` rather than receiving synthetic
defaults.

For incremental trace batches, nonblank canonical-root metadata replaces metadata
from a provisional child root. Once the canonical root is stable, the first
nonblank value for each metadata field is retained and missing or conflicting
retries cannot erase or oscillate it. Service discovery separately represents the
latest service observation: `firstSeen` is the earliest span start, `lastSeen` is
the latest span end, missing metadata never erases known values, and newer
observed nonblank metadata supersedes older values.

#### Critical-path semantics

ArgusIQ distinguishes five latency measures:

- **Trace wall-clock duration** is the envelope from the earliest valid span
  start to the latest valid span end, including gaps and disconnected components.
- **Sum of span durations** adds every span independently and can double-count
  nested or concurrent work. It is diagnostic context, not trace latency.
- **Longest individual span** is only the maximum span interval. It is not a
  distributed-trace critical path.
- **Span self-time** is a span interval minus the union of its validated direct
  child intervals. It is never negative and does not double-count overlapping
  children.
- **Critical-path duration** is the sum of self-time on the canonical root
  component plus the maximum-duration set of non-overlapping, causally linked
  child paths. Sequential siblings can contribute; overlapping siblings compete
  through deterministic weighted interval scheduling. Equal-weight alternatives
  retain the earlier-ending schedule, with start time and span ID ordering any
  remaining ties.

The persisted `critical_path_duration_ms` is recomputed from the complete stored
span union on every idempotent OTLP merge. Trace detail derives structured
evidence from the same deterministic algorithm: quality (`COMPLETE`, `PARTIAL`,
or `UNAVAILABLE`), graph limitations, and ordered contributing spans with span
duration, self-time, and critical contribution. Missing parents, multiple roots,
out-of-bounds children, malformed timestamps, duplicate identities, disconnected
components, and cycles are never silently converted into causal edges. A valid
canonical component may produce `PARTIAL` evidence; otherwise the result is
`UNAVAILABLE`. This avoids a schema migration while keeping the summary scalar
queryable and the detailed evidence reproducible from persisted spans.

The computation loads spans for one trace, performs no per-span repository
queries, uses iterative graph walks, and runs in O(n log n) time and O(n) space.

#### Metrics semantics

Global analytics are aggregates over persisted traces. `totalTraces` is a count,
not throughput. `requestsPerMinute`, `requestsPerHour`, and `requestsPerDay` are
counts inside closed rolling UTC windows ending at query time. Latency averages,
bounds, and continuous p50/p90/p95/p99 percentiles are calculated by PostgreSQL;
latency and percentage fields are `null` when no observations provide a valid
denominator.

Service identity comes from discovered `service.name` resource attributes.
Service request metrics count observed `SERVER` spans, and operation metrics
aggregate observed spans by service and operation name. Error rate and success
rate are shares of observed requests; success rate is not infrastructure
availability. `firstSeen`, `lastSeen`, and `observationAgeMinutes` describe the
telemetry observation period and do not claim process uptime. `telemetryStatus`
is an evidence-limited signal: `ACTIVE` means recently observed without a high
recent error share, `ERRORING` means the last five minutes contain at least 10%
erroring observed requests, and `STALE` means no telemetry was observed for five
minutes. In particular, `STALE` does not prove that a service is unavailable.

Analytics summaries and endpoint rankings cover persisted history. Service
detail includes separately bounded recent traces and recent errors (up to ten),
while the shared frontend live window remains recent activity rather than an
authoritative aggregate.

#### Existing databases created by Hibernate

Flyway automatic baselining is deliberately disabled. An existing non-empty
database without `flyway_schema_history` will fail startup instead of being
silently claimed or modified. Before adopting Flyway for such a database:

1. Back up the database and stop all writers.
2. Compare its complete schema with
   `backend/src/main/resources/db/migration/V1__initial_argusiq_schema.sql`.
3. Audit identity conflicts and span ownership without changing data:

   ```sql
   SELECT trace_id, count(*)
   FROM traces
   GROUP BY trace_id
   HAVING count(*) > 1;

   SELECT trace_id, span_id, count(*)
   FROM spans
   GROUP BY trace_id, span_id
   HAVING count(*) > 1;

   SELECT s.id, s.trace_id, s.trace_entity_id, t.trace_id AS owner_trace_id
   FROM spans s
   LEFT JOIN traces t ON t.id = s.trace_entity_id
   WHERE s.trace_entity_id IS NULL
      OR t.id IS NULL
      OR s.trace_id <> t.trace_id;
   ```

4. If any query returns rows, decide the canonical records outside the migration.
   The migration never deletes or deduplicates telemetry.
5. Only after the schema and data have been audited, perform a one-time Flyway
   baseline at version `1` (using the Flyway CLI or temporary
   `SPRING_FLYWAY_BASELINE_ON_MIGRATE=true` and
   `SPRING_FLYWAY_BASELINE_VERSION=1` for one controlled startup). Remove those
   temporary settings immediately afterward. Hibernate validation must then pass.

#### Database tests

Fast integration tests retain H2 in PostgreSQL compatibility mode for semantic
regression coverage, but Flyway creates their schema and Hibernate validates it.
PostgreSQL locking, uniqueness, rollback, and concurrent OTLP behavior are covered
by `PostgresPersistenceIntegrationTest`, which starts and removes PostgreSQL
automatically through Testcontainers. Docker Desktop or another
Testcontainers-compatible Docker runtime must be running; no database credentials
are required in tracked test configuration.

```bash
cd backend
JAVA_HOME=/path/to/jdk-21 ./mvnw -Dtest=PostgresPersistenceIntegrationTest test
JAVA_HOME=/path/to/jdk-21 ./mvnw test
```

### Frontend

```bash
cd frontend
npm install
npm run dev
```

Frontend:
http://localhost:5173

Backend:
http://localhost:8080
