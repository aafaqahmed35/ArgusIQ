package com.argusiq.tracing.repository;

import com.argusiq.tracing.entity.TraceLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface TraceLogRepository extends JpaRepository<TraceLog, Long> {

    List<TraceLog> findTop5ByOrderByExecutionTimeMsDesc();

    @Query("select avg(traceLog.executionTimeMs) from TraceLog traceLog")
    Double findAverageExecutionTimeMs();
}
