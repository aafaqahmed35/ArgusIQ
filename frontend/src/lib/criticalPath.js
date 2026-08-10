/**
 * Critical Path Engine for ArgusIQ.
 * Computes the longest sequential blocking chain from root to leaf spans
 * and identifies bottleneck services and operations.
 */

export function computeCriticalPath(treeData) {
  const { rootNodes, totalDurationMs, spanMap } = treeData

  if (!rootNodes || rootNodes.length === 0) {
    return {
      criticalPathSpanIds: new Set(),
      criticalPathNodes: [],
      totalCriticalPathMs: 0,
      criticalPathPercentage: 0,
      slowestSpan: null,
      slowestService: null,
      bottleneckOperation: null,
    }
  }

  // Helper to find the heaviest path down the tree recursively
  function getHeaviestPath(node) {
    if (!node.children || node.children.length === 0) {
      return {
        path: [node],
        weight: node.durationMs || 0,
      }
    }

    let heaviestChildPath = { path: [], weight: 0 }

    for (const child of node.children) {
      const childResult = getHeaviestPath(child)
      if (childResult.weight > heaviestChildPath.weight) {
        heaviestChildPath = childResult
      }
    }

    return {
      path: [node, ...heaviestChildPath.path],
      weight: (node.durationMs || 0) + heaviestChildPath.weight,
    }
  }

  // Find root node with maximum tree depth/weight
  let maxPathResult = { path: [], weight: 0 }

  for (const root of rootNodes) {
    const res = getHeaviestPath(root)
    if (res.weight > maxPathResult.weight) {
      maxPathResult = res
    }
  }

  const criticalPathNodes = maxPathResult.path
  const criticalPathSpanIds = new Set(criticalPathNodes.map((n) => n.spanId))

  // Find slowest individual span in the entire trace
  let slowestSpan = null
  let maxSpanDuration = -1

  spanMap.forEach((node) => {
    if (node.durationMs > maxSpanDuration) {
      maxSpanDuration = node.durationMs
      slowestSpan = node
    }
  })

  // Compute per-service aggregate duration to find bottleneck service
  const serviceDurations = new Map()
  spanMap.forEach((node) => {
    const sName = node.serviceName || 'Unknown Service'
    const current = serviceDurations.get(sName) || 0
    serviceDurations.set(sName, current + node.durationMs)
  })

  let slowestService = 'None'
  let maxServiceDur = -1

  serviceDurations.forEach((dur, service) => {
    if (dur > maxServiceDur) {
      maxServiceDur = dur
      slowestService = service
    }
  })

  const totalCriticalPathMs = maxPathResult.weight
  const criticalPathPercentage = totalDurationMs > 0
    ? Math.min(100, Math.round((totalCriticalPathMs / totalDurationMs) * 100))
    : 0

  return {
    criticalPathSpanIds,
    criticalPathNodes,
    totalCriticalPathMs,
    criticalPathPercentage,
    slowestSpan,
    slowestService,
    bottleneckOperation: slowestSpan ? slowestSpan.name : 'N/A',
  }
}
