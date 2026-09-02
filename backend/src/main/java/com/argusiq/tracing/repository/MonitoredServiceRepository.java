package com.argusiq.tracing.repository;

import com.argusiq.tracing.entity.MonitoredService;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MonitoredServiceRepository extends JpaRepository<MonitoredService, Long> {
    Optional<MonitoredService> findByServiceName(String serviceName);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from MonitoredService s where s.serviceName = :serviceName")
    Optional<MonitoredService> findByServiceNameForUpdate(@Param("serviceName") String serviceName);

    @Query("select s.serviceName from MonitoredService s order by s.serviceName asc")
    java.util.List<String> findAllServiceNames();
}
