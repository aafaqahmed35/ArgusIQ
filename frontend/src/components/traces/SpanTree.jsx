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
      while (current && current.parentSpanId) {
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
      <div style={{ padding: '2rem', textAlign: 'center', color: '#94A3B8' }}>
        No spans found for tree visualization.
      </div>
    )
  }

  return (
    <div className="span-tree-container" style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
      {/* Controls Bar */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '0.4rem 0.75rem',
          background: 'rgba(10, 25, 47, 0.6)',
          border: '1px solid rgba(255, 255, 255, 0.08)',
          borderRadius: '6px',
          fontSize: '0.75rem',
          color: '#94A3B8',
        }}
      >
        <span>
          Showing <strong>{flatNodes.length}</strong> of <strong>{spanMap.size}</strong> spans
        </span>
        <div style={{ display: 'flex', gap: '0.5rem' }}>
          <button
            type="button"
            onClick={handleExpandAll}
            style={{
              background: 'rgba(255,255,255,0.05)',
              border: '1px solid rgba(255,255,255,0.1)',
              color: '#F5F7FA',
              borderRadius: '4px',
              padding: '2px 8px',
              cursor: 'pointer',
              fontSize: '0.75rem',
            }}
          >
            Expand All
          </button>
          <button
            type="button"
            onClick={handleCollapseAll}
            style={{
              background: 'rgba(255,255,255,0.05)',
              border: '1px solid rgba(255,255,255,0.1)',
              color: '#F5F7FA',
              borderRadius: '4px',
              padding: '2px 8px',
              cursor: 'pointer',
              fontSize: '0.75rem',
            }}
          >
            Collapse All
          </button>
        </div>
      </div>

      {/* Tree Rows Container */}
      <div
        tabIndex={0}
        style={{
          background: '#07131F',
          border: '1px solid rgba(255, 255, 255, 0.08)',
          borderRadius: '6px',
          overflowX: 'auto',
          maxHeight: '520px',
          outline: 'none',
        }}
      >
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
