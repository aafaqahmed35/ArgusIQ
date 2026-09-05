package com.argusiq.tracing.criticalpath;

import com.argusiq.tracing.entity.SpanEntity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Shared structural-evidence rules for accepting reported parent/child edges.
 */
public final class TraceStructuralEvidence {

    private TraceStructuralEvidence() {
    }

    public static Map<String, String> validatedParentSpanIds(Collection<SpanEntity> spans) {
        if (spans == null || spans.isEmpty()) {
            return Map.of();
        }

        Map<String, SpanEntity> spansById = new LinkedHashMap<>();
        boolean duplicateIdentity = false;
        boolean traceIdMismatch = false;
        String observedTraceId = null;
        for (SpanEntity span : spans) {
            if (span == null || span.getSpanId() == null || span.getSpanId().isBlank() || !validInterval(span)) {
                continue;
            }
            if (observedTraceId == null) {
                observedTraceId = span.getTraceId();
            } else if (!Objects.equals(observedTraceId, span.getTraceId())) {
                traceIdMismatch = true;
            }
            if (spansById.putIfAbsent(span.getSpanId(), span) != null) {
                duplicateIdentity = true;
            }
        }
        if (duplicateIdentity || traceIdMismatch) {
            return Map.of();
        }

        Set<String> cycleSpanIds = cycleSpanIds(spansById.values());
        Map<String, String> validatedParents = new HashMap<>();
        for (SpanEntity child : spansById.values()) {
            String parentSpanId = child.getParentSpanId();
            if (parentSpanId == null || parentSpanId.isBlank() || cycleSpanIds.contains(child.getSpanId())) {
                continue;
            }
            SpanEntity parent = spansById.get(parentSpanId);
            if (parent == null || cycleSpanIds.contains(parentSpanId) || !containedBy(child, parent)) {
                continue;
            }
            validatedParents.put(child.getSpanId(), parentSpanId);
        }
        return Map.copyOf(validatedParents);
    }

    static Set<String> cycleSpanIds(Collection<SpanEntity> spans) {
        Map<String, SpanEntity> spansById = new HashMap<>();
        spans.forEach(span -> spansById.put(span.getSpanId(), span));
        Set<String> processed = new HashSet<>();
        Set<String> cycleSpanIds = new HashSet<>();
        List<SpanEntity> ordered = spansById.values().stream()
                .sorted(java.util.Comparator.comparing(SpanEntity::getSpanId))
                .toList();
        for (SpanEntity start : ordered) {
            if (processed.contains(start.getSpanId())) {
                continue;
            }
            List<SpanEntity> path = new ArrayList<>();
            Map<String, Integer> position = new HashMap<>();
            SpanEntity current = start;
            while (current != null && !processed.contains(current.getSpanId())) {
                Integer cycleStart = position.get(current.getSpanId());
                if (cycleStart != null) {
                    for (int index = cycleStart; index < path.size(); index++) {
                        cycleSpanIds.add(path.get(index).getSpanId());
                    }
                    break;
                }
                position.put(current.getSpanId(), path.size());
                path.add(current);
                String parentSpanId = current.getParentSpanId();
                current = parentSpanId == null || parentSpanId.isBlank() ? null : spansById.get(parentSpanId);
            }
            path.forEach(span -> processed.add(span.getSpanId()));
        }
        return Set.copyOf(cycleSpanIds);
    }

    static boolean validInterval(SpanEntity span) {
        return span.getStartTime() != null
                && span.getEndTime() != null
                && !span.getEndTime().isBefore(span.getStartTime());
    }

    static boolean containedBy(SpanEntity child, SpanEntity parent) {
        return !child.getStartTime().isBefore(parent.getStartTime())
                && !child.getEndTime().isAfter(parent.getEndTime());
    }
}
