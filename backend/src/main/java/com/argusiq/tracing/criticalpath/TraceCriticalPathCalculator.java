package com.argusiq.tracing.criticalpath;

import com.argusiq.tracing.entity.SpanEntity;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Computes a causally constrained, interval-aware critical path for one trace.
 *
 * <p>For every validated span, exclusive/self-time is its timestamp interval
 * minus the union of its validated direct-child intervals. Sequential sibling
 * paths may all be selected. Overlapping sibling paths compete through weighted
 * interval scheduling, so their durations are never blindly summed. The weight
 * of a child is its recursively computed critical-path duration.</p>
 *
 * <p>Parent cycles, malformed intervals, missing parents, out-of-bounds children,
 * duplicate identities, and disconnected components are reported explicitly.
 * No invalid edge is used to fabricate causality. All graph walks are iterative,
 * including post-order evaluation, so a malformed or very deep trace cannot
 * cause recursive overflow. Complexity is O(n log n) time and O(n) space for
 * n spans (the per-parent child sorts sum to O(n log n)).</p>
 */
@Component
public class TraceCriticalPathCalculator {

    private static final Comparator<Node> ROOT_ORDER = Comparator
            .comparingInt(TraceCriticalPathCalculator::rootRank)
            .thenComparing(Node::start)
            .thenComparing(Node::id);

    private static final Comparator<Node> INTERVAL_END_ORDER = Comparator
            .comparing(Node::end)
            .thenComparing(Node::start)
            .thenComparing(Node::id);

    private static final Comparator<Node> EXECUTION_ORDER = Comparator
            .comparing(Node::start)
            .thenComparing(Node::end)
            .thenComparing(Node::id);

    public CriticalPathResult calculate(Collection<SpanEntity> spans, String preferredRootSpanId) {
        if (spans == null || spans.isEmpty()) {
            return unavailable(0, 0, 0, "NO_SPANS");
        }

        LinkedHashSet<String> issues = new LinkedHashSet<>();
        Map<String, Node> nodesById = new LinkedHashMap<>();
        List<Node> metricNodes = new ArrayList<>();
        boolean duplicateIdentity = false;
        boolean traceIdMismatch = false;
        String observedTraceId = null;

        for (SpanEntity span : spans) {
            if (span == null || span.getSpanId() == null || span.getSpanId().isBlank()) {
                issues.add("MISSING_SPAN_ID");
                continue;
            }
            if (!TraceStructuralEvidence.validInterval(span)) {
                issues.add("MALFORMED_SPAN_TIMESTAMPS");
                continue;
            }
            if (observedTraceId == null) {
                observedTraceId = span.getTraceId();
            } else if (!java.util.Objects.equals(observedTraceId, span.getTraceId())) {
                traceIdMismatch = true;
            }

            Node node = new Node(span);
            metricNodes.add(node);
            Node previous = nodesById.putIfAbsent(span.getSpanId(), node);
            if (previous != null) {
                duplicateIdentity = true;
            }
        }

        TraceMetrics metrics = metrics(metricNodes);
        if (duplicateIdentity) {
            issues.add("DUPLICATE_SPAN_ID");
        }
        if (traceIdMismatch) {
            issues.add("TRACE_ID_MISMATCH");
        }
        if (duplicateIdentity || traceIdMismatch) {
            return unavailable(metrics.wallClockMs(), metrics.sumMs(), metrics.longestMs(), issues);
        }
        if (nodesById.isEmpty()) {
            return unavailable(metrics.wallClockMs(), metrics.sumMs(), metrics.longestMs(), issues);
        }

        Set<String> cycleIds = TraceStructuralEvidence.cycleSpanIds(
                nodesById.values().stream().map(Node::span).toList()
        );
        Map<String, String> validatedParentSpanIds = TraceStructuralEvidence.validatedParentSpanIds(spans);
        if (!cycleIds.isEmpty()) {
            issues.add("CYCLIC_PARENT_GRAPH");
        }

        List<Node> usableNodes = nodesById.values().stream()
                .filter(node -> !cycleIds.contains(node.id()))
                .toList();
        Map<String, Node> usableById = new HashMap<>();
        usableNodes.forEach(node -> usableById.put(node.id(), node));
        if (preferredRootSpanId != null && !usableById.containsKey(preferredRootSpanId)) {
            issues.add("PREFERRED_ROOT_UNAVAILABLE");
            return unavailable(metrics.wallClockMs(), metrics.sumMs(), metrics.longestMs(), issues);
        }

        for (Node child : usableNodes) {
            if (noParent(child)) {
                continue;
            }
            Node parent = usableById.get(child.parentId());
            if (parent == null) {
                issues.add(nodesById.containsKey(child.parentId()) ? "CYCLIC_PARENT_GRAPH" : "MISSING_PARENT");
                continue;
            }
            if (!parent.id().equals(validatedParentSpanIds.get(child.id()))) {
                issues.add("CHILD_OUTSIDE_PARENT_BOUNDS");
                continue;
            }
            child.validParent = parent;
            parent.children.add(child);
        }

        List<Node> naturalRoots = usableNodes.stream().filter(TraceCriticalPathCalculator::noParent).sorted(ROOT_ORDER).toList();
        if (naturalRoots.size() > 1) {
            issues.add("MULTIPLE_ROOT_CANDIDATES");
        }

        Node root = preferredRootSpanId != null ? usableById.get(preferredRootSpanId) : null;
        if (root != null) {
            while (root.validParent != null) {
                root = root.validParent;
            }
        } else {
            root = usableNodes.stream().filter(node -> node.validParent == null).min(ROOT_ORDER).orElse(null);
        }
        if (root == null) {
            return unavailable(metrics.wallClockMs(), metrics.sumMs(), metrics.longestMs(), issues);
        }

        List<Node> reachablePostOrder = postOrder(root);
        Set<String> reachableIds = new HashSet<>();
        reachablePostOrder.forEach(node -> reachableIds.add(node.id()));
        if (reachableIds.size() != usableNodes.size() || cycleIds.size() > 0) {
            issues.add("DISCONNECTED_SPANS");
        }

        Map<String, PathValue> pathBySpanId = new HashMap<>();
        for (Node node : reachablePostOrder) {
            long selfTimeMs = exclusiveTimeMs(node);
            List<Node> selectedChildren = selectNonOverlappingChildren(node.children, pathBySpanId);
            long totalMs = selfTimeMs;
            selectedChildren.sort(EXECUTION_ORDER);
            for (Node child : selectedChildren) {
                PathValue childPath = pathBySpanId.get(child.id());
                totalMs = safeAdd(totalMs, childPath.totalMs());
            }
            pathBySpanId.put(node.id(), new PathValue(totalMs, selfTimeMs, List.copyOf(selectedChildren)));
        }

        PathValue result = pathBySpanId.get(root.id());
        List<CriticalPathSpanContribution> evidence = selectedEvidence(root, pathBySpanId);
        CriticalPathResult.Status status = issues.isEmpty()
                ? CriticalPathResult.Status.COMPLETE
                : CriticalPathResult.Status.PARTIAL;
        return new CriticalPathResult(
                CriticalPathResult.ALGORITHM,
                status,
                root.id(),
                result.totalMs(),
                metrics.wallClockMs(),
                metrics.sumMs(),
                metrics.longestMs(),
                evidence,
                List.copyOf(issues)
        );
    }

    private static List<CriticalPathSpanContribution> selectedEvidence(
            Node root,
            Map<String, PathValue> pathBySpanId
    ) {
        List<CriticalPathSpanContribution> evidence = new ArrayList<>();
        Deque<Node> stack = new ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            Node node = stack.pop();
            PathValue path = pathBySpanId.get(node.id());
            evidence.add(contribution(node, path.selfTimeMs()));
            List<Node> selectedChildren = path.selectedChildren();
            for (int index = selectedChildren.size() - 1; index >= 0; index--) {
                stack.push(selectedChildren.get(index));
            }
        }
        return List.copyOf(evidence);
    }

    private static List<Node> postOrder(Node root) {
        List<Node> order = new ArrayList<>();
        Deque<Visit> stack = new ArrayDeque<>();
        stack.push(new Visit(root, false));
        while (!stack.isEmpty()) {
            Visit visit = stack.pop();
            if (visit.expanded()) {
                order.add(visit.node());
                continue;
            }
            stack.push(new Visit(visit.node(), true));
            List<Node> children = new ArrayList<>(visit.node().children);
            children.sort(EXECUTION_ORDER.reversed());
            for (Node child : children) {
                stack.push(new Visit(child, false));
            }
        }
        return order;
    }

    private static List<Node> selectNonOverlappingChildren(List<Node> children, Map<String, PathValue> pathBySpanId) {
        if (children.isEmpty()) {
            return new ArrayList<>();
        }
        List<Node> sorted = new ArrayList<>(children);
        sorted.sort(INTERVAL_END_ORDER);
        int count = sorted.size();
        int[] predecessor = new int[count];
        long[] best = new long[count + 1];
        boolean[] take = new boolean[count + 1];

        for (int index = 0; index < count; index++) {
            predecessor[index] = lastCompatible(sorted, index);
            long include = safeAdd(pathBySpanId.get(sorted.get(index).id()).totalMs(), best[predecessor[index] + 1]);
            long exclude = best[index];
            if (include > exclude) {
                best[index + 1] = include;
                take[index + 1] = true;
            } else {
                // On equal weight retain the already selected, earlier-ending
                // schedule. End/start/span-ID sorting makes the tie deterministic.
                best[index + 1] = exclude;
            }
        }

        List<Node> selected = new ArrayList<>();
        for (int index = count; index > 0;) {
            if (take[index]) {
                Node child = sorted.get(index - 1);
                selected.add(child);
                index = predecessor[index - 1] + 1;
            } else {
                index--;
            }
        }
        return selected;
    }

    private static int lastCompatible(List<Node> sorted, int index) {
        int low = 0;
        int high = index - 1;
        int answer = -1;
        LocalDateTime start = sorted.get(index).start();
        while (low <= high) {
            int middle = (low + high) >>> 1;
            if (!sorted.get(middle).end().isAfter(start)) {
                answer = middle;
                low = middle + 1;
            } else {
                high = middle - 1;
            }
        }
        return answer;
    }

    private static long exclusiveTimeMs(Node node) {
        if (node.children.isEmpty()) {
            return node.durationMs();
        }
        List<Node> children = new ArrayList<>(node.children);
        children.sort(EXECUTION_ORDER);
        LocalDateTime unionStart = null;
        LocalDateTime unionEnd = null;
        long coveredMs = 0;
        for (Node child : children) {
            if (unionStart == null) {
                unionStart = child.start();
                unionEnd = child.end();
            } else if (!child.start().isAfter(unionEnd)) {
                if (child.end().isAfter(unionEnd)) {
                    unionEnd = child.end();
                }
            } else {
                coveredMs = safeAdd(coveredMs, millisBetween(unionStart, unionEnd));
                unionStart = child.start();
                unionEnd = child.end();
            }
        }
        coveredMs = safeAdd(coveredMs, millisBetween(unionStart, unionEnd));
        return Math.max(0L, node.durationMs() - coveredMs);
    }

    private static TraceMetrics metrics(Collection<Node> nodes) {
        if (nodes.isEmpty()) {
            return new TraceMetrics(0, 0, 0);
        }
        LocalDateTime earliest = null;
        LocalDateTime latest = null;
        long sum = 0;
        long longest = 0;
        for (Node node : nodes) {
            earliest = earliest == null || node.start().isBefore(earliest) ? node.start() : earliest;
            latest = latest == null || node.end().isAfter(latest) ? node.end() : latest;
            sum = safeAdd(sum, node.durationMs());
            longest = Math.max(longest, node.durationMs());
        }
        return new TraceMetrics(millisBetween(earliest, latest), sum, longest);
    }

    private static CriticalPathSpanContribution contribution(Node node, long selfTimeMs) {
        SpanEntity span = node.span();
        return new CriticalPathSpanContribution(
                span.getSpanId(),
                span.getParentSpanId(),
                span.getServiceName(),
                span.getName(),
                span.getKind(),
                span.getStartTime(),
                span.getEndTime(),
                node.durationMs(),
                selfTimeMs,
                selfTimeMs
        );
    }

    private static boolean noParent(Node node) {
        return node.parentId() == null || node.parentId().isBlank();
    }

    private static int rootRank(Node node) {
        if (noParent(node) && "SERVER".equalsIgnoreCase(node.span().getKind())) {
            return 0;
        }
        return noParent(node) ? 1 : 2;
    }

    private static long millisBetween(LocalDateTime start, LocalDateTime end) {
        try {
            return Math.max(0L, Duration.between(start, end).toMillis());
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }

    private static long safeAdd(long left, long right) {
        if (right > 0 && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    private static CriticalPathResult unavailable(long wallClockMs, long sumMs, long longestMs, String issue) {
        return unavailable(wallClockMs, sumMs, longestMs, List.of(issue));
    }

    private static CriticalPathResult unavailable(
            long wallClockMs,
            long sumMs,
            long longestMs,
            Collection<String> issues
    ) {
        return new CriticalPathResult(
                CriticalPathResult.ALGORITHM,
                CriticalPathResult.Status.UNAVAILABLE,
                null,
                0,
                wallClockMs,
                sumMs,
                longestMs,
                List.of(),
                List.copyOf(issues)
        );
    }

    private static final class Node {
        private final SpanEntity span;
        private final long durationMs;
        private final List<Node> children = new ArrayList<>();
        private Node validParent;

        private Node(SpanEntity span) {
            this.span = span;
            this.durationMs = millisBetween(span.getStartTime(), span.getEndTime());
        }

        private SpanEntity span() { return span; }
        private String id() { return span.getSpanId(); }
        private String parentId() { return span.getParentSpanId(); }
        private LocalDateTime start() { return span.getStartTime(); }
        private LocalDateTime end() { return span.getEndTime(); }
        private long durationMs() { return durationMs; }
    }

    private record Visit(Node node, boolean expanded) { }
    private record PathValue(long totalMs, long selfTimeMs, List<Node> selectedChildren) { }
    private record TraceMetrics(long wallClockMs, long sumMs, long longestMs) { }
}
