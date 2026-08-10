package com.argusiq.tracing.repository;

import com.argusiq.tracing.entity.Alert;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertRepository extends JpaRepository<Alert, Long> {
}
