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

}
