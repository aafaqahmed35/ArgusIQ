package com.argusiq.tracing.repository;

import com.argusiq.tracing.entity.SavedSearch;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SavedSearchRepository extends JpaRepository<SavedSearch, Long> {
}
