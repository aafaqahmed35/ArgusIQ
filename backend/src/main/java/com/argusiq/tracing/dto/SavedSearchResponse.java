package com.argusiq.tracing.dto;

import java.time.LocalDateTime;

public class SavedSearchResponse {
    private final Long id;
    private final String name;
    private final String filtersJson;
    private final LocalDateTime createdAt;
    private final LocalDateTime lastUsedAt;

    public SavedSearchResponse(Long id, String name, String filtersJson, LocalDateTime createdAt, LocalDateTime lastUsedAt) {
        this.id = id;
        this.name = name;
        this.filtersJson = filtersJson;
        this.createdAt = createdAt;
        this.lastUsedAt = lastUsedAt;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getFiltersJson() { return filtersJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getLastUsedAt() { return lastUsedAt; }
}
