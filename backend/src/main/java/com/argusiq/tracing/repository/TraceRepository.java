package com.argusiq.tracing.repository;

import com.argusiq.tracing.entity.TraceEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TraceRepository extends JpaRepository<TraceEntity, Long> {

    @EntityGraph(attributePaths = "spans")
    Optional<TraceEntity> findFirstByTraceId(String traceId);

    @Query("SELECT DISTINCT t FROM TraceEntity t LEFT JOIN FETCH t.spans WHERE t.traceId = :traceId")
    Optional<TraceEntity> findByTraceIdWithSpans(@Param("traceId") String traceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM TraceEntity t WHERE t.traceId = :traceId")
    Optional<TraceEntity> findByTraceIdForUpdate(@Param("traceId") String traceId);

    List<TraceEntity> findTop5ByOrderByDurationMsDesc();

    @Query("SELECT AVG(t.durationMs) FROM TraceEntity t")
    Double findAverageDurationMs();

    @Query("SELECT t.statusCode, COUNT(t) FROM TraceEntity t GROUP BY t.statusCode")
    List<Object[]> findStatusCodeDistribution();

    @Query("SELECT t.httpMethod, COUNT(t) FROM TraceEntity t GROUP BY t.httpMethod")
    List<Object[]> findHttpMethodDistribution();

    @Query("""
            SELECT t FROM TraceEntity t
            WHERE t.serviceName = :serviceName
               OR EXISTS (SELECT s.id FROM SpanEntity s WHERE s.trace = t AND s.serviceName = :serviceName)
            ORDER BY t.startTime DESC, t.id DESC
            """)
    List<TraceEntity> findRecentByServiceName(@Param("serviceName") String serviceName, org.springframework.data.domain.Pageable pageable);

    @Query("""
            SELECT t FROM TraceEntity t
            WHERE (t.serviceName = :serviceName AND UPPER(t.statusCode) = 'ERROR')
               OR EXISTS (SELECT s.id FROM SpanEntity s
                          WHERE s.trace = t
                            AND s.serviceName = :serviceName
                            AND UPPER(s.statusCode) = 'ERROR')
            ORDER BY t.startTime DESC, t.id DESC
            """)
    List<TraceEntity> findRecentErrorsByServiceName(@Param("serviceName") String serviceName, org.springframework.data.domain.Pageable pageable);
}
