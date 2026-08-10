package com.argusiq.tracing.repository;

import com.argusiq.tracing.entity.SpanEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SpanRepository extends JpaRepository<SpanEntity, Long> {

    Optional<SpanEntity> findFirstBySpanId(String spanId);

    List<SpanEntity> findByTraceId(String traceId);

    long countByTraceId(String traceId);

    @Query("select count(distinct s.serviceName) from SpanEntity s where s.traceId = :traceId")
    long countDistinctServicesByTraceId(@Param("traceId") String traceId);

    @Query("select s.serviceName, count(s) from SpanEntity s group by s.serviceName order by count(s) desc")
    List<Object[]> findMostActiveServices();

    @Query("select s.name, s.serviceName, avg(s.durationMs), max(s.durationMs), count(s) from SpanEntity s group by s.name, s.serviceName order by avg(s.durationMs) desc")
    List<Object[]> findMostExpensiveOperations();

    @Query("select distinct s.serviceName from SpanEntity s where s.traceId = :traceId and s.serviceName is not null")
    List<String> findDistinctServiceNamesByTraceId(@Param("traceId") String traceId);

    @Query("""
            select distinct parent.serviceName, child.serviceName
            from SpanEntity child
            join SpanEntity parent on child.traceId = parent.traceId and child.parentSpanId = parent.spanId
            where child.serviceName is not null
              and parent.serviceName is not null
              and child.serviceName <> parent.serviceName
            """)
    List<Object[]> findServiceDependencies();

    @Query("""
            select s.name, count(s)
            from SpanEntity s
            where s.serviceName = :serviceName and upper(s.statusCode) = 'ERROR'
            group by s.name
            order by count(s) desc
            """)
    List<Object[]> findMostCommonErrors(@Param("serviceName") String serviceName);

    @Query("""
            select s.name, count(s)
            from SpanEntity s
            where s.serviceName = :serviceName
            group by s.name
            order by count(s) desc
            """)
    List<Object[]> findTopOperations(@Param("serviceName") String serviceName);

    @Query("select count(s) from SpanEntity s where s.serviceName = :serviceName and s.startTime >= :since")
    long countByServiceSince(@Param("serviceName") String serviceName, @Param("since") LocalDateTime since);
}
