package com.argusiq.tracing.dto;

import java.util.List;

public class DependencyGraphResponse {
    private final List<String> nodes;
    private final List<DependencyEdgeDto> edges;

    public DependencyGraphResponse(List<String> nodes, List<DependencyEdgeDto> edges) {
        this.nodes = nodes != null ? nodes : List.of();
        this.edges = edges != null ? edges : List.of();
    }

    public List<String> getNodes() { return nodes; }
    public List<DependencyEdgeDto> getEdges() { return edges; }
}
