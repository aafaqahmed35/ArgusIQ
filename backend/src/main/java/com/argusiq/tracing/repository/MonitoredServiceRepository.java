package com.argusiq.tracing.repository;

import com.argusiq.tracing.entity.MonitoredService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface MonitoredServiceRepository extends JpaRepository<MonitoredService, Long> {
    Optional<MonitoredService> findByServiceName(String serviceName);

    @Query("select s.serviceName from MonitoredService s order by s.serviceName asc")
    java.util.List<String> findAllServiceNames();
}
