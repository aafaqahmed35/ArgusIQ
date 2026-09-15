package com.argusiq.tracing.repository;

import com.argusiq.tracing.entity.AlertRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface AlertRuleRepository extends JpaRepository<AlertRule, Long> {
    List<AlertRule> findByEnabledTrueOrderByIdAsc();

    List<AlertRule> findAllByOrderByIdAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT rule FROM AlertRule rule WHERE rule.id = :id")
    Optional<AlertRule> findByIdForUpdate(@Param("id") Long id);
}
