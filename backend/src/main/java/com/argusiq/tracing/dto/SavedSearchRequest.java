package com.argusiq.tracing.dto;

import java.util.Map;

public class SavedSearchRequest {
    private String name;
    private Map<String, Object> filters;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Map<String, Object> getFilters() { return filters; }
    public void setFilters(Map<String, Object> filters) { this.filters = filters; }
}
