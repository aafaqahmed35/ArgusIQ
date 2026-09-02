package com.argusiq.tracing.service;

import com.argusiq.tracing.dto.PageResponse;
import com.argusiq.tracing.dto.SavedSearchRequest;
import com.argusiq.tracing.dto.SavedSearchResponse;
import com.argusiq.tracing.dto.TraceResponseDto;
import com.argusiq.tracing.dto.TraceSearchCriteria;
import com.argusiq.tracing.entity.SavedSearch;
import com.argusiq.tracing.entity.TraceEntity;
import com.argusiq.tracing.mapper.OtlpMapper;
import com.argusiq.tracing.repository.SavedSearchRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class SearchService {

    private static final Set<String> SORT_COLUMNS = Set.of(
            "startTime", "durationMs", "statusCode", "httpMethod", "requestUri", "serviceName", "traceId"
    );

    private final EntityManager entityManager;
    private final OtlpMapper otlpMapper;
    private final SavedSearchRepository savedSearchRepository;

    public SearchService(EntityManager entityManager, OtlpMapper otlpMapper, SavedSearchRepository savedSearchRepository) {
        this.entityManager = entityManager;
        this.otlpMapper = otlpMapper;
        this.savedSearchRepository = savedSearchRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<TraceResponseDto> searchTraces(TraceSearchCriteria criteria) {
        validate(criteria);
        QueryParts parts = buildQuery(criteria, false);
        TypedQuery<TraceEntity> query = entityManager.createQuery(parts.jpql(), TraceEntity.class);
        parts.parameters().forEach(query::setParameter);
        long offset = (long) criteria.getPage() * criteria.getSize();
        if (offset > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("page and size produce an unsupported offset");
        }
        query.setFirstResult((int) offset);
        query.setMaxResults(criteria.getSize());

        QueryParts countParts = buildQuery(criteria, true);
        TypedQuery<Long> countQuery = entityManager.createQuery(countParts.jpql(), Long.class);
        countParts.parameters().forEach(countQuery::setParameter);
        long total = countQuery.getSingleResult();

        List<TraceResponseDto> content = query.getResultList().stream()
                .map(otlpMapper::mapToTraceResponseDto)
                .toList();
        return new PageResponse<>(content, criteria.getPage(), criteria.getSize(), total);
    }

    @Transactional
    public SavedSearchResponse saveSearch(SavedSearchRequest request) {
        String filtersJson = toJson(request.getFilters() != null ? request.getFilters() : Map.of());
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        SavedSearch saved = savedSearchRepository.save(new SavedSearch(request.getName(), filtersJson, now, null));
        return mapSavedSearch(saved);
    }

    @Transactional(readOnly = true)
    public List<SavedSearchResponse> getSavedSearches() {
        return savedSearchRepository.findAll().stream().map(this::mapSavedSearch).toList();
    }

    @Transactional
    public SavedSearchResponse markSavedSearchUsed(Long id) {
        SavedSearch saved = savedSearchRepository.findById(id).orElseThrow();
        saved.setLastUsedAt(LocalDateTime.now(ZoneOffset.UTC));
        return mapSavedSearch(savedSearchRepository.save(saved));
    }

    private QueryParts buildQuery(TraceSearchCriteria criteria, boolean count) {
        boolean joinSpans = hasSpanFilters(criteria);
        String select = count ? "select count(distinct t)" : "select distinct t";
        StringBuilder jpql = new StringBuilder(select).append(" from TraceEntity t");
        if (joinSpans) {
            jpql.append(" join t.spans s");
        }

        List<String> clauses = new ArrayList<>();
        Map<String, Object> params = new java.util.LinkedHashMap<>();

        addFreeText(clauses, params, criteria.getQuery());
        addEquals(clauses, params, "t.traceId", "traceId", criteria.getTraceId());
        addLike(clauses, params, "t.requestUri", "endpoint", criteria.getEndpoint());
        addEqualsIgnoreCase(clauses, params, "t.httpMethod", "httpMethod", criteria.getHttpMethod());
        addEqualsIgnoreCase(clauses, params, "t.statusCode", "status", criteria.getStatus());
        addEqualsIgnoreCase(clauses, params, "t.serviceName", "serviceExact", criteria.getServiceExact());
        addLike(clauses, params, "t.serviceName", "service", criteria.getService());
        addLike(clauses, params, "t.rootSpanName", "operation", criteria.getOperation());
        addLike(clauses, params, "t.businessOperation", "businessOperation", criteria.getBusinessOperation());

        if (criteria.getDuration() != null) {
            clauses.add("t.durationMs = :duration");
            params.put("duration", criteria.getDuration());
        }
        if (criteria.getMinDuration() != null) {
            clauses.add("t.durationMs >= :minDuration");
            params.put("minDuration", criteria.getMinDuration());
        }
        if (criteria.getMaxDuration() != null) {
            clauses.add("t.durationMs <= :maxDuration");
            params.put("maxDuration", criteria.getMaxDuration());
        }
        if (criteria.getFrom() != null) {
            clauses.add("t.startTime >= :from");
            params.put("from", criteria.getFrom());
        }
        if (criteria.getTo() != null) {
            clauses.add("t.startTime <= :to");
            params.put("to", criteria.getTo());
        }
        if (joinSpans) {
            addEquals(clauses, params, "s.spanId", "spanId", criteria.getSpanId());
            addLike(clauses, params, "s.customerId", "customerId", criteria.getCustomerId());
            addLike(clauses, params, "s.accountId", "accountId", criteria.getAccountId());
            addLike(clauses, params, "s.loanId", "loanId", criteria.getLoanId());
            addLike(clauses, params, "s.transactionId", "transactionId", criteria.getTransactionId());
            if (criteria.getStatusCode() != null) {
                clauses.add("s.httpStatusCode = :statusCode");
                params.put("statusCode", criteria.getStatusCode());
            }
            if (Boolean.TRUE.equals(criteria.getRootSpan())) {
                clauses.add("(s.parentSpanId is null or s.parentSpanId = '')");
            }
        }

        if (!clauses.isEmpty()) {
            jpql.append(" where ").append(String.join(" and ", clauses));
        }
        if (!count) {
            String sortBy = criteria.getSortBy();
            String direction = criteria.getSortDirection().toLowerCase(Locale.ROOT);
            jpql.append(" order by t.").append(sortBy).append(" ").append(direction);
            if (!"id".equals(sortBy)) {
                jpql.append(", t.id ").append(direction);
            }
        }
        return new QueryParts(jpql.toString(), params);
    }

    private void validate(TraceSearchCriteria criteria) {
        if (criteria == null) {
            throw new IllegalArgumentException("search criteria are required");
        }
        if (criteria.getPage() < 0) {
            throw new IllegalArgumentException("page must be at least 0");
        }
        if (criteria.getSize() < 1 || criteria.getSize() > TraceSearchCriteria.MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + TraceSearchCriteria.MAX_PAGE_SIZE);
        }
        if (!SORT_COLUMNS.contains(criteria.getSortBy())) {
            throw new IllegalArgumentException("unsupported sortBy value");
        }
        if (!"asc".equalsIgnoreCase(criteria.getSortDirection())
                && !"desc".equalsIgnoreCase(criteria.getSortDirection())) {
            throw new IllegalArgumentException("sortDirection must be asc or desc");
        }
        if (!criteria.isDurationRangeValid()) {
            throw new IllegalArgumentException("minDuration must be less than or equal to maxDuration");
        }
        if (!criteria.isTimeRangeValid()) {
            throw new IllegalArgumentException("from must be less than or equal to to");
        }
    }

    private void addFreeText(List<String> clauses, Map<String, Object> params, String value) {
        if (!hasText(value)) {
            return;
        }

        clauses.add("(" + String.join(" or ",
                "lower(t.traceId) like :query",
                "lower(t.serviceName) like :query",
                "lower(t.rootSpanName) like :query",
                "lower(t.requestUri) like :query",
                "lower(t.httpMethod) like :query",
                "lower(t.statusCode) like :query"
        ) + ")");
        params.put("query", "%" + value.toLowerCase(Locale.ROOT).trim() + "%");
    }

    private boolean hasSpanFilters(TraceSearchCriteria criteria) {
        return hasText(criteria.getSpanId())
                || hasText(criteria.getCustomerId())
                || hasText(criteria.getAccountId())
                || hasText(criteria.getLoanId())
                || hasText(criteria.getTransactionId())
                || criteria.getStatusCode() != null
                || Boolean.TRUE.equals(criteria.getRootSpan());
    }

    private void addLike(List<String> clauses, Map<String, Object> params, String field, String param, String value) {
        if (hasText(value)) {
            clauses.add("lower(" + field + ") like :" + param);
            params.put(param, "%" + value.toLowerCase(Locale.ROOT).trim() + "%");
        }
    }

    private void addEquals(List<String> clauses, Map<String, Object> params, String field, String param, String value) {
        if (hasText(value)) {
            clauses.add(field + " = :" + param);
            params.put(param, value.trim());
        }
    }

    private void addEqualsIgnoreCase(List<String> clauses, Map<String, Object> params, String field, String param, String value) {
        if (hasText(value)) {
            clauses.add("lower(" + field + ") = :" + param);
            params.put(param, value.toLowerCase(Locale.ROOT).trim());
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private SavedSearchResponse mapSavedSearch(SavedSearch savedSearch) {
        return new SavedSearchResponse(savedSearch.getId(), savedSearch.getName(), savedSearch.getFiltersJson(), savedSearch.getCreatedAt(), savedSearch.getLastUsedAt());
    }

    private String toJson(Map<String, Object> filters) {
        if (filters.isEmpty()) {
            return "{}";
        }
        return filters.entrySet().stream()
                .map(entry -> "\"" + escape(entry.getKey()) + "\":\"" + escape(String.valueOf(entry.getValue())) + "\"")
                .collect(java.util.stream.Collectors.joining(",", "{", "}"));
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record QueryParts(String jpql, Map<String, Object> parameters) {
    }
}
