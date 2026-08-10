package com.argusiq.tracing.controller;

import com.argusiq.tracing.dto.PageResponse;
import com.argusiq.tracing.dto.SavedSearchRequest;
import com.argusiq.tracing.dto.SavedSearchResponse;
import com.argusiq.tracing.dto.TraceResponseDto;
import com.argusiq.tracing.dto.TraceSearchCriteria;
import com.argusiq.tracing.service.SearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    public PageResponse<TraceResponseDto> search(@ModelAttribute TraceSearchCriteria criteria) {
        return searchService.searchTraces(criteria);
    }

    @GetMapping("/traces")
    public PageResponse<TraceResponseDto> searchTraces(@ModelAttribute TraceSearchCriteria criteria) {
        return searchService.searchTraces(criteria);
    }

    @GetMapping("/saved")
    public List<SavedSearchResponse> getSavedSearches() {
        return searchService.getSavedSearches();
    }

    @PostMapping("/saved")
    public SavedSearchResponse saveSearch(@RequestBody SavedSearchRequest request) {
        return searchService.saveSearch(request);
    }

    @PostMapping("/saved/{id}/use")
    public ResponseEntity<SavedSearchResponse> markUsed(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(searchService.markSavedSearchUsed(id));
        } catch (RuntimeException ex) {
            return ResponseEntity.notFound().build();
        }
    }
}
