package com.argusiq.tracing.repository;

import com.argusiq.tracing.entity.TraceEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TraceRepository extends JpaRepository<TraceEntity, Long> {

    @EntityGraph(attributePaths = "spans")
    Optional<TraceEntity> findFirstByTraceId(String traceId);

    @Query("SELECT DISTINCT t FROM TraceEntity t LEFT JOIN FETCH t.spans WHERE t.traceId = :traceId")
    Optional<TraceEntity> findByTraceIdWithSpans(@Param("traceId") String traceId);

    List<TraceEntity> findTop5ByOrderByDurationMsDesc();

    @Query("SELECT AVG(t.durationMs) FROM TraceEntity t")
    Double findAverageDurationMs();

    @Query("SELECT MIN(t.durationMs) FROM TraceEntity t")
    Long findMinDurationMs();

    @Query("SELECT MAX(t.durationMs) FROM TraceEntity t")
    Long findMaxDurationMs();

    @Query("SELECT COUNT(DISTINCT t.requestUri) FROM TraceEntity t")
    long countUniqueEndpoints();

    @Query("SELECT COUNT(DISTINCT t.serviceName) FROM TraceEntity t")
    long countUniqueServices();

    @Query("SELECT COUNT(t) FROM TraceEntity t WHERE UPPER(t.statusCode) = 'ERROR'")
    long countErrors();

    @Query("SELECT COUNT(t) FROM TraceEntity t WHERE t.startTime >= :since")
    long countSince(@Param("since") LocalDateTime since);

    @Query("SELECT t.requestUri, AVG(t.durationMs), COUNT(t) FROM TraceEntity t WHERE t.requestUri IS NOT NULL GROUP BY t.requestUri ORDER BY AVG(t.durationMs) DESC")
    List<Object[]> findEndpointLatencyRankingDesc();

    @Query("SELECT t.requestUri, AVG(t.durationMs), COUNT(t) FROM TraceEntity t WHERE t.requestUri IS NOT NULL GROUP BY t.requestUri ORDER BY AVG(t.durationMs) ASC")
    List<Object[]> findEndpointLatencyRankingAsc();

    @Query("SELECT t.requestUri, COUNT(t) FROM TraceEntity t WHERE UPPER(t.statusCode) = 'ERROR' AND t.requestUri IS NOT NULL GROUP BY t.requestUri ORDER BY COUNT(t) DESC")
    List<Object[]> findMostFailingEndpoints();

    @Query("SELECT t.statusCode, COUNT(t) FROM TraceEntity t GROUP BY t.statusCode")
    List<Object[]> findStatusCodeDistribution();

    @Query("SELECT t.httpMethod, COUNT(t) FROM TraceEntity t GROUP BY t.httpMethod")
    List<Object[]> findHttpMethodDistribution();

    @Query("SELECT t.durationMs FROM TraceEntity t ORDER BY t.durationMs ASC")
    List<Long> findAllDurationsSorted();

    @Query("SELECT t.durationMs, COUNT(t) FROM TraceEntity t GROUP BY t.durationMs ORDER BY t.durationMs ASC")
    List<Object[]> findDurationHistogramSource();

    @Query("SELECT t.serviceName, AVG(t.durationMs), COUNT(t), SUM(CASE WHEN UPPER(t.statusCode) = 'ERROR' THEN 1 ELSE 0 END) FROM TraceEntity t GROUP BY t.serviceName")
    List<Object[]> findServiceMetricRows();

    @Query("SELECT t FROM TraceEntity t WHERE t.serviceName = :serviceName ORDER BY t.startTime DESC")
    List<TraceEntity> findRecentByServiceName(@Param("serviceName") String serviceName, org.springframework.data.domain.Pageable pageable);

    @Query("SELECT t FROM TraceEntity t WHERE t.serviceName = :serviceName AND UPPER(t.statusCode) = 'ERROR' ORDER BY t.startTime DESC")
    List<TraceEntity> findRecentErrorsByServiceName(@Param("serviceName") String serviceName, org.springframework.data.domain.Pageable pageable);

    @Query("SELECT t.requestUri, COUNT(t) FROM TraceEntity t WHERE t.serviceName = :serviceName AND t.requestUri IS NOT NULL GROUP BY t.requestUri ORDER BY COUNT(t) DESC")
    List<Object[]> findTopEndpointsByServiceName(@Param("serviceName") String serviceName);

    @Query("SELECT AVG(t.durationMs) FROM TraceEntity t WHERE t.serviceName = :serviceName")
    Double findAverageDurationMsByServiceName(@Param("serviceName") String serviceName);

    @Query("SELECT MAX(t.durationMs) FROM TraceEntity t WHERE t.serviceName = :serviceName")
    Long findMaxDurationMsByServiceName(@Param("serviceName") String serviceName);

    @Query("SELECT COUNT(t) FROM TraceEntity t WHERE t.serviceName = :serviceName")
    long countByServiceName(@Param("serviceName") String serviceName);

    @Query("SELECT COUNT(t) FROM TraceEntity t WHERE t.serviceName = :serviceName AND UPPER(t.statusCode) = 'ERROR'")
    long countErrorsByServiceName(@Param("serviceName") String serviceName);

    @Query("SELECT t.durationMs FROM TraceEntity t WHERE t.serviceName = :serviceName ORDER BY t.durationMs ASC")
    List<Long> findDurationsSortedByServiceName(@Param("serviceName") String serviceName);
}
