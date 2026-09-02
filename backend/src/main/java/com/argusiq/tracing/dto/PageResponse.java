package com.argusiq.tracing.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;

public class PageResponse<T> {

    private final List<T> items;
    private final int page;
    private final int size;
    private final long totalItems;
    private final int totalPages;
    private final boolean hasNext;
    private final boolean hasPrevious;

    public PageResponse(List<T> items, int page, int size, long totalItems) {
        this.items = items != null ? items : List.of();
        this.page = page;
        this.size = size;
        this.totalItems = totalItems;
        this.totalPages = size > 0 ? (int) Math.ceil((double) totalItems / size) : 0;
        this.hasNext = page + 1 < totalPages;
        this.hasPrevious = page > 0;
    }

    public List<T> getItems() {
        return items;
    }

    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }

    public long getTotalItems() {
        return totalItems;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public boolean isHasNext() {
        return hasNext;
    }

    public boolean isHasPrevious() {
        return hasPrevious;
    }

    @JsonIgnore
    public List<T> getContent() {
        return items;
    }

    @JsonIgnore
    public long getTotalElements() {
        return totalItems;
    }
}
