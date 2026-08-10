package com.argusiq.tracing.repository;

import com.argusiq.tracing.entity.AlertRule;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertRuleRepository extends JpaRepository<AlertRule, Long> {
}
