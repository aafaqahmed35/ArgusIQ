package com.argusiq.tracing.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class TelemetryAnalyticsRepository {

    private static final String TRACE_AGGREGATE_SQL = """
            SELECT COUNT(*) AS total_traces,
                   AVG(duration_ms) AS average_latency_ms,
                   PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY duration_ms) AS p50_latency_ms,
                   PERCENTILE_CONT(0.9) WITHIN GROUP (ORDER BY duration_ms) AS p90_latency_ms,
                   PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY duration_ms) AS p95_latency_ms,
                   PERCENTILE_CONT(0.99) WITHIN GROUP (ORDER BY duration_ms) AS p99_latency_ms,
                   MIN(duration_ms) AS minimum_latency_ms,
                   MAX(duration_ms) AS maximum_latency_ms,
                   COUNT(CASE WHEN UPPER(status_code) = 'ERROR' THEN 1 END) AS error_count,
                   COUNT(CASE WHEN start_time >= ? AND start_time <= ? THEN 1 END) AS requests_per_minute,
                   COUNT(CASE WHEN start_time >= ? AND start_time <= ? THEN 1 END) AS requests_per_hour,
                   COUNT(CASE WHEN start_time >= ? AND start_time <= ? THEN 1 END) AS requests_per_day,
                   COUNT(DISTINCT request_uri) AS unique_endpoints,
                   (SELECT COUNT(*) FROM services) AS unique_services,
                   COUNT(CASE WHEN duration_ms BETWEEN 0 AND 100 THEN 1 END) AS duration_0_100,
                   COUNT(CASE WHEN duration_ms BETWEEN 101 AND 250 THEN 1 END) AS duration_101_250,
                   COUNT(CASE WHEN duration_ms BETWEEN 251 AND 500 THEN 1 END) AS duration_251_500,
                   COUNT(CASE WHEN duration_ms BETWEEN 501 AND 1000 THEN 1 END) AS duration_501_1000,
                   COUNT(CASE WHEN duration_ms BETWEEN 1001 AND 2500 THEN 1 END) AS duration_1001_2500,
                   COUNT(CASE WHEN duration_ms > 2500 THEN 1 END) AS duration_2501_plus
            FROM traces
            """;

    private static final String ENDPOINT_AGGREGATE_SQL = """
            SELECT request_uri AS endpoint,
                   COUNT(*) AS request_count,
                   AVG(duration_ms) AS average_latency_ms,
                   PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY duration_ms) AS p95_latency_ms,
                   COUNT(CASE WHEN UPPER(status_code) = 'ERROR' THEN 1 END) AS error_count,
                   MIN(duration_ms) AS minimum_latency_ms,
                   MAX(duration_ms) AS maximum_latency_ms
            FROM traces
            WHERE request_uri IS NOT NULL
            GROUP BY request_uri
            """;

    private static final String SERVICE_AGGREGATE_SELECT = """
            SELECT service_name,
                   COUNT(*) AS request_count,
                   AVG(duration_ms) AS average_latency_ms,
                   PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY duration_ms) AS p95_latency_ms,
                   PERCENTILE_CONT(0.99) WITHIN GROUP (ORDER BY duration_ms) AS p99_latency_ms,
                   MIN(duration_ms) AS minimum_latency_ms,
                   MAX(duration_ms) AS maximum_latency_ms,
                   COUNT(CASE WHEN UPPER(status_code) = 'ERROR' THEN 1 END) AS error_count,
                   COUNT(CASE WHEN start_time >= ? AND start_time <= ? THEN 1 END) AS requests_per_minute,
                   COUNT(CASE WHEN start_time >= ? AND start_time <= ? THEN 1 END) AS requests_last_five_minutes,
                   COUNT(CASE WHEN start_time >= ? AND start_time <= ? AND UPPER(status_code) = 'ERROR' THEN 1 END) AS errors_last_five_minutes
            FROM spans
            WHERE UPPER(kind) = 'SERVER'
            """;

    private static final String OPERATION_AGGREGATE_SELECT = """
            SELECT service_name,
                   name AS operation_name,
                   COUNT(*) AS observation_count,
                   AVG(duration_ms) AS average_latency_ms,
                   MIN(duration_ms) AS minimum_latency_ms,
                   MAX(duration_ms) AS maximum_latency_ms,
                   COUNT(CASE WHEN UPPER(status_code) = 'ERROR' THEN 1 END) AS error_count
            FROM spans
            """;

    private final JdbcTemplate jdbcTemplate;

    public TelemetryAnalyticsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public GlobalTraceAggregate globalTraceAggregate(LocalDateTime now) {
        return jdbcTemplate.queryForObject(
                TRACE_AGGREGATE_SQL,
                this::mapGlobalTraceAggregate,
                now.minusMinutes(1), now,
                now.minusHours(1), now,
                now.minusDays(1), now
        );
    }

    public List<EndpointAggregate> endpointAggregates(String sortBy, String sortDirection, int limit) {
        String sortColumn = switch (sortBy) {
            case "traffic" -> "request_count";
            case "errors" -> "error_count";
            case "latency" -> "average_latency_ms";
            default -> throw new IllegalArgumentException("sortBy must be traffic, latency, or errors");
        };
        String direction = switch (sortDirection.toLowerCase()) {
            case "asc" -> "ASC";
            case "desc" -> "DESC";
            default -> throw new IllegalArgumentException("sortDirection must be asc or desc");
        };
        String sql = ENDPOINT_AGGREGATE_SQL
                + " ORDER BY " + sortColumn + " " + direction + ", request_uri ASC LIMIT ?";
        return jdbcTemplate.query(sql, this::mapEndpointAggregate, limit);
    }

    public List<ServiceAggregate> serviceAggregates(LocalDateTime now) {
        String sql = SERVICE_AGGREGATE_SELECT + " GROUP BY service_name";
        return jdbcTemplate.query(
                sql,
                this::mapServiceAggregate,
                now.minusMinutes(1), now,
                now.minusMinutes(5), now,
                now.minusMinutes(5), now
        );
    }

    public ServiceAggregate serviceAggregate(String serviceName, LocalDateTime now) {
        String sql = SERVICE_AGGREGATE_SELECT + " AND service_name = ? GROUP BY service_name";
        List<ServiceAggregate> rows = jdbcTemplate.query(
                sql,
                this::mapServiceAggregate,
                now.minusMinutes(1), now,
                now.minusMinutes(5), now,
                now.minusMinutes(5), now,
                serviceName
        );
        return rows.isEmpty() ? ServiceAggregate.empty(serviceName) : rows.getFirst();
    }

    public List<OperationAggregate> operationAggregates() {
        return jdbcTemplate.query(
                OPERATION_AGGREGATE_SELECT + " GROUP BY service_name, name",
                this::mapOperationAggregate
        );
    }

    public List<OperationAggregate> operationAggregates(String serviceName) {
        return jdbcTemplate.query(
                OPERATION_AGGREGATE_SELECT + " WHERE service_name = ? GROUP BY service_name, name",
                this::mapOperationAggregate,
                serviceName
        );
    }

    public List<OperationAggregate> slowestOperationAggregates(int limit) {
        return jdbcTemplate.query(
                OPERATION_AGGREGATE_SELECT
                        + " GROUP BY service_name, name"
                        + " ORDER BY average_latency_ms DESC, service_name ASC, name ASC LIMIT ?",
                this::mapOperationAggregate,
                limit
        );
    }

    private GlobalTraceAggregate mapGlobalTraceAggregate(ResultSet resultSet, int rowNumber) throws SQLException {
        return new GlobalTraceAggregate(
                resultSet.getLong("total_traces"),
                nullableDouble(resultSet, "average_latency_ms"),
                nullableDouble(resultSet, "p50_latency_ms"),
                nullableDouble(resultSet, "p90_latency_ms"),
                nullableDouble(resultSet, "p95_latency_ms"),
                nullableDouble(resultSet, "p99_latency_ms"),
                nullableLong(resultSet, "minimum_latency_ms"),
                nullableLong(resultSet, "maximum_latency_ms"),
                resultSet.getLong("error_count"),
                resultSet.getLong("requests_per_minute"),
                resultSet.getLong("requests_per_hour"),
                resultSet.getLong("requests_per_day"),
                resultSet.getLong("unique_endpoints"),
                resultSet.getLong("unique_services"),
                resultSet.getLong("duration_0_100"),
                resultSet.getLong("duration_101_250"),
                resultSet.getLong("duration_251_500"),
                resultSet.getLong("duration_501_1000"),
                resultSet.getLong("duration_1001_2500"),
                resultSet.getLong("duration_2501_plus")
        );
    }

    private EndpointAggregate mapEndpointAggregate(ResultSet resultSet, int rowNumber) throws SQLException {
        return new EndpointAggregate(
                resultSet.getString("endpoint"),
                resultSet.getLong("request_count"),
                nullableDouble(resultSet, "average_latency_ms"),
                nullableDouble(resultSet, "p95_latency_ms"),
                resultSet.getLong("error_count"),
                nullableLong(resultSet, "minimum_latency_ms"),
                nullableLong(resultSet, "maximum_latency_ms")
        );
    }

    private ServiceAggregate mapServiceAggregate(ResultSet resultSet, int rowNumber) throws SQLException {
        return new ServiceAggregate(
                resultSet.getString("service_name"),
                resultSet.getLong("request_count"),
                nullableDouble(resultSet, "average_latency_ms"),
                nullableDouble(resultSet, "p95_latency_ms"),
                nullableDouble(resultSet, "p99_latency_ms"),
                nullableLong(resultSet, "minimum_latency_ms"),
                nullableLong(resultSet, "maximum_latency_ms"),
                resultSet.getLong("error_count"),
                resultSet.getLong("requests_per_minute"),
                resultSet.getLong("requests_last_five_minutes"),
                resultSet.getLong("errors_last_five_minutes")
        );
    }

    private OperationAggregate mapOperationAggregate(ResultSet resultSet, int rowNumber) throws SQLException {
        return new OperationAggregate(
                resultSet.getString("service_name"),
                resultSet.getString("operation_name"),
                resultSet.getLong("observation_count"),
                nullableDouble(resultSet, "average_latency_ms"),
                nullableLong(resultSet, "minimum_latency_ms"),
                nullableLong(resultSet, "maximum_latency_ms"),
                resultSet.getLong("error_count")
        );
    }

    private Double nullableDouble(ResultSet resultSet, String column) throws SQLException {
        Number value = (Number) resultSet.getObject(column);
        return value != null ? value.doubleValue() : null;
    }

    private Long nullableLong(ResultSet resultSet, String column) throws SQLException {
        Number value = (Number) resultSet.getObject(column);
        return value != null ? value.longValue() : null;
    }

    public record GlobalTraceAggregate(
            long totalTraces,
            Double averageLatencyMs,
            Double p50LatencyMs,
            Double p90LatencyMs,
            Double p95LatencyMs,
            Double p99LatencyMs,
            Long minimumLatencyMs,
            Long maximumLatencyMs,
            long errorCount,
            long requestsPerMinute,
            long requestsPerHour,
            long requestsPerDay,
            long uniqueEndpoints,
            long uniqueServices,
            long duration0To100,
            long duration101To250,
            long duration251To500,
            long duration501To1000,
            long duration1001To2500,
            long duration2501Plus
    ) {
    }

    public record EndpointAggregate(
            String endpoint,
            long requestCount,
            Double averageLatencyMs,
            Double p95LatencyMs,
            long errorCount,
            Long minimumLatencyMs,
            Long maximumLatencyMs
    ) {
    }

    public record ServiceAggregate(
            String serviceName,
            long requestCount,
            Double averageLatencyMs,
            Double p95LatencyMs,
            Double p99LatencyMs,
            Long minimumLatencyMs,
            Long maximumLatencyMs,
            long errorCount,
            long requestsPerMinute,
            long requestsLastFiveMinutes,
            long errorsLastFiveMinutes
    ) {
        public static ServiceAggregate empty(String serviceName) {
            return new ServiceAggregate(serviceName, 0, null, null, null, null, null, 0, 0, 0, 0);
        }
    }

    public record OperationAggregate(
            String serviceName,
            String operationName,
            long observationCount,
            Double averageLatencyMs,
            Long minimumLatencyMs,
            Long maximumLatencyMs,
            long errorCount
    ) {
    }
}
