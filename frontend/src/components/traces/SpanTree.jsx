import { useState, useMemo, useCallback } from 'react'
import { flattenSpanTree } from '../../lib/spanTreeBuilder'
import SpanNode from './SpanNode'

function SpanTree({
  treeData,
  selectedSpanId,
  criticalPathSpanIds = new Set(),
  onSelectSpan,
  searchQuery = '',
}) {
  const { rootNodes, spanMap } = treeData

  // 1. Manage expanded state for nodes (default: all expanded)
  const [expandedIds, setExpandedIds] = useState(() => {
    const ids = new Set()
    spanMap.forEach((_, spanId) => ids.add(spanId))
    return ids
  })

  // 2. Expand all matching search nodes automatically
  const matchingSpanIds = useMemo(() => {
    if (!searchQuery || !searchQuery.trim()) return new Set()
    const q = searchQuery.toLowerCase().trim()
    const matches = new Set()

    spanMap.forEach((node, spanId) => {
      const match =
        node.name?.toLowerCase().includes(q) ||
        node.serviceName?.toLowerCase().includes(q) ||
        node.spanId?.toLowerCase().includes(q) ||
        node.statusCode?.toLowerCase().includes(q)

      if (match) {
        matches.add(spanId)
      }
    })

    return matches
  }, [searchQuery, spanMap])

  // Ensure parents of matching nodes are expanded
  const effectiveExpandedIds = useMemo(() => {
    if (matchingSpanIds.size === 0) return expandedIds

    const nextSet = new Set(expandedIds)
    matchingSpanIds.forEach((spanId) => {
      let current = spanMap.get(spanId)
      const visited = new Set()
      while (current && current.parentSpanId && !visited.has(current.spanId)) {
        visited.add(current.spanId)
        nextSet.add(current.parentSpanId)
        current = spanMap.get(current.parentSpanId)
      }
    })
    return nextSet
  }, [expandedIds, matchingSpanIds, spanMap])

  const handleToggleExpand = useCallback((spanId) => {
    setExpandedIds((prev) => {
      const next = new Set(prev)
      if (next.has(spanId)) {
        next.delete(spanId)
      } else {
        next.add(spanId)
      }
      return next
    })
  }, [])

  const handleExpandAll = () => {
    const all = new Set()
    spanMap.forEach((_, id) => all.add(id))
    setExpandedIds(all)
  }

  const handleCollapseAll = () => {
    setExpandedIds(new Set())
  }

  // 3. Flatten tree for rendering
  const flatNodes = useMemo(
    () => flattenSpanTree(rootNodes, effectiveExpandedIds),
    [rootNodes, effectiveExpandedIds]
  )

  if (!rootNodes || rootNodes.length === 0) {
    return (
      <div className="trace-visualization-empty">
        No spans found for tree visualization.
      </div>
    )
  }

  return (
    <div className="span-tree-container">
      <div className="trace-visualization-toolbar">
        <span>
          Showing <strong>{flatNodes.length}</strong> of <strong>{spanMap.size}</strong> spans
        </span>
        <div className="trace-visualization-toolbar__actions">
          <button
            className="trace-visualization-button"
            type="button"
            onClick={handleExpandAll}
          >
            Expand all
          </button>
          <button
            className="trace-visualization-button"
            type="button"
            onClick={handleCollapseAll}
          >
            Collapse all
          </button>
        </div>
      </div>

      <div className="span-tree-container__rows" aria-label="Trace span hierarchy">
        {flatNodes.map((node) => (
          <SpanNode
            key={node.spanId}
            node={node}
            isSelected={node.spanId === selectedSpanId}
            isCriticalPath={criticalPathSpanIds.has(node.spanId)}
            isMatchingSearch={matchingSpanIds.has(node.spanId)}
            onSelectNode={onSelectSpan}
            onToggleExpand={handleToggleExpand}
          />
        ))}
      </div>
    </div>
  )
}

export default SpanTree
