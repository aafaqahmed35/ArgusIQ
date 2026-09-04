/**
 * Adapts the backend's authoritative critical-path evidence to trace UI nodes.
 * The browser intentionally does not reconstruct causality from span durations.
 */
export function computeCriticalPath(treeData, criticalPathResult) {
  const empty = {
    status: null,
    issues: [],
    criticalPathSpanIds: new Set(),
    criticalPathNodes: [],
    totalCriticalPathMs: 0,
    criticalPathPercentage: 0,
    largestContributor: null,
  }

  if (!criticalPathResult) return empty

  const evidence = Array.isArray(criticalPathResult.spans) ? criticalPathResult.spans : []
  const criticalPathNodes = evidence.map((item) => {
    const node = treeData.spanMap.get(item.spanId) || {}
    return {
      ...node,
      ...item,
      name: item.operationName || node.name || 'unnamed-span',
      durationMs: item.durationMs ?? node.durationMs ?? 0,
      contributionDurationMs: item.contributionDurationMs ?? 0,
    }
  })
  const criticalPathSpanIds = new Set(criticalPathNodes.map((node) => node.spanId))
  const totalCriticalPathMs = Number(criticalPathResult.totalDurationMs) || 0
  const wallClockMs = Number(criticalPathResult.traceWallClockDurationMs) || treeData.totalDurationMs || 0
  const criticalPathPercentage = wallClockMs > 0
    ? Math.min(100, Math.round((totalCriticalPathMs / wallClockMs) * 100))
    : 0
  const largestContributor = criticalPathNodes.reduce(
    (largest, node) => !largest || node.contributionDurationMs > largest.contributionDurationMs ? node : largest,
    null
  )

  return {
    status: criticalPathResult.status || 'UNAVAILABLE',
    issues: Array.isArray(criticalPathResult.issues) ? criticalPathResult.issues : [],
    algorithm: criticalPathResult.algorithm,
    criticalPathSpanIds,
    criticalPathNodes,
    totalCriticalPathMs,
    criticalPathPercentage,
    largestContributor,
  }
}
