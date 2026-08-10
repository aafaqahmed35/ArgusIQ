/**
 * Utility for parsing flat SpanDto collections into a hierarchical tree structure.
 * Runs in O(N) time using a single map pass.
 */

function parseTime(timestamp) {
  if (!timestamp) return 0
  const date = new Date(timestamp)
  const time = date.getTime()
  return Number.isNaN(time) ? 0 : time
}

export function buildSpanTree(spans = [], traceSummary = {}) {
  if (!Array.isArray(spans) || spans.length === 0) {
    return {
      rootNodes: [],
      spanMap: new Map(),
      totalDurationMs: traceSummary?.durationMs || traceSummary?.executionTimeMs || 1,
      minStartTime: 0,
      maxEndTime: 0,
      spanCount: 0,
    }
  }

  // 1. Determine min start time and max end time
  let minStartTime = Number.MAX_SAFE_INTEGER
  let maxEndTime = 0

  spans.forEach((span) => {
    const start = parseTime(span.startTime)
    const end = parseTime(span.endTime)
    if (start > 0 && start < minStartTime) minStartTime = start
    if (end > 0 && end > maxEndTime) maxEndTime = end
  })

  if (minStartTime === Number.MAX_SAFE_INTEGER) minStartTime = Date.now()
  if (maxEndTime <= minStartTime) {
    const rootDuration = traceSummary?.durationMs || traceSummary?.executionTimeMs || 0
    maxEndTime = minStartTime + Math.max(rootDuration, 1)
  }

  const totalDurationMs = Math.max(maxEndTime - minStartTime, traceSummary?.durationMs || 1)

  // 2. Map spans to tree node objects
  const spanMap = new Map()
  const rootNodes = []

  spans.forEach((span) => {
    const start = parseTime(span.startTime)
    const end = parseTime(span.endTime)
    const startOffsetMs = Math.max(0, start - minStartTime)
    const spanDurationMs = span.durationMs ?? (end > start ? end - start : 0)
    const relativeStartPct = (startOffsetMs / totalDurationMs) * 100
    const relativeDurationPct = Math.max(0.5, (spanDurationMs / totalDurationMs) * 100)

    const node = {
      ...span,
      spanId: span.spanId || `span-${Math.random()}`,
      parentSpanId: span.parentSpanId || null,
      name: span.name || 'unnamed-span',
      serviceName: span.serviceName || traceSummary?.serviceName || 'AtlasBank',
      kind: span.kind || 'INTERNAL',
      statusCode: (span.statusCode || 'OK').toUpperCase(),
      statusMessage: span.statusMessage || '',
      startOffsetMs,
      durationMs: spanDurationMs,
      relativeStartPct: Math.min(100, Math.max(0, relativeStartPct)),
      relativeDurationPct: Math.min(100, relativeDurationPct),
      children: [],
      depth: 0,
    }

    spanMap.set(node.spanId, node)
  })

  // 3. Assemble parent-child tree hierarchy
  spanMap.forEach((node) => {
    if (node.parentSpanId && spanMap.has(node.parentSpanId)) {
      const parent = spanMap.get(node.parentSpanId)
      node.depth = parent.depth + 1
      parent.children.push(node)
    } else {
      rootNodes.push(node)
    }
  })

  // Sort children by start offset
  const sortChildren = (nodes) => {
    nodes.sort((a, b) => a.startOffsetMs - b.startOffsetMs)
    nodes.forEach((node) => {
      if (node.children.length > 0) {
        sortChildren(node.children)
      }
    })
  }

  sortChildren(rootNodes)

  return {
    rootNodes,
    spanMap,
    totalDurationMs,
    minStartTime,
    maxEndTime,
    spanCount: spans.length,
  }
}

/**
 * Flatten a span tree for virtualized / indexed list rendering.
 */
export function flattenSpanTree(nodes = [], expandedIds = new Set(), depth = 0) {
  const result = []

  const traverse = (list, currentDepth) => {
    list.forEach((node) => {
      const isExpanded = expandedIds.has(node.spanId)
      result.push({
        ...node,
        depth: currentDepth,
        hasChildren: node.children.length > 0,
        isExpanded,
      })

      if (hasChildrenAndExpanded(node, expandedIds)) {
        traverse(node.children, currentDepth + 1)
      }
    })
  }

  traverse(nodes, depth)
  return result
}

function hasChildrenAndExpanded(node, expandedIds) {
  return node.children.length > 0 && expandedIds.has(node.spanId)
}
