package com.argusiq.tracing.repository;

import com.argusiq.tracing.entity.Alert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AlertRepository extends JpaRepository<Alert, Long> {
    List<Alert> findAllByOrderByLastTriggeredAtDescAlertIdDesc();

    Optional<Alert> findByActiveKey(String activeKey);
}
