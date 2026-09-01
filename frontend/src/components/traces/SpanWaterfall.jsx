import { useState, useMemo } from 'react'
import { flattenSpanTree } from '../../lib/spanTreeBuilder'
import WaterfallBar from './WaterfallBar'

function SpanWaterfall({
  treeData,
  selectedSpanId,
  criticalPathSpanIds = new Set(),
  onSelectSpan,
}) {
  const { rootNodes, totalDurationMs, spanMap } = treeData
  const [zoomLevel, setZoomLevel] = useState(1)

  // Expand all nodes for waterfall presentation
  const expandedAllIds = useMemo(() => {
    const ids = new Set()
    spanMap.forEach((_, spanId) => ids.add(spanId))
    return ids
  }, [spanMap])

  const flatNodes = useMemo(
    () => flattenSpanTree(rootNodes, expandedAllIds),
    [rootNodes, expandedAllIds]
  )

  const handleZoomIn = () => setZoomLevel((prev) => Math.min(prev + 0.5, 4))
  const handleZoomOut = () => setZoomLevel((prev) => Math.max(prev - 0.25, 0.75))
  const handleResetZoom = () => setZoomLevel(1)

  // Time ruler ticks (0%, 25%, 50%, 75%, 100%)
  const rulerTicks = [0, 0.25, 0.5, 0.75, 1.0].map((fraction) => ({
    pct: fraction * 100,
    label: `${Math.round(totalDurationMs * fraction)} ms`,
  }))

  if (!rootNodes || rootNodes.length === 0) {
    return (
      <div style={{ padding: '2rem', textAlign: 'center', color: '#94A3B8' }}>
        No spans available to render waterfall timeline.
      </div>
    )
  }

  return (
    <div className="span-waterfall-container" style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
      {/* Header Controls & Zoom Toolbar */}
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
        <div>
          Total Trace Duration: <strong style={{ color: '#F5F7FA' }}>{totalDurationMs} ms</strong> ({spanMap.size} spans)
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
          <button
            type="button"
            onClick={handleZoomOut}
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
            Zoom -
          </button>
          <span style={{ fontSize: '0.75rem', color: '#38bdf8', fontFamily: 'monospace' }}>
            {Math.round(zoomLevel * 100)}%
          </span>
          <button
            type="button"
            onClick={handleZoomIn}
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
            Zoom +
          </button>
          <button
            type="button"
            onClick={handleResetZoom}
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
            Reset
          </button>
        </div>
      </div>

      {/* Main Waterfall Timeline Table */}
      <div
        style={{
          background: '#07131F',
          border: '1px solid rgba(255, 255, 255, 0.08)',
          borderRadius: '6px',
          overflowX: 'auto',
          maxHeight: '520px',
        }}
      >
        {/* Sticky Millisecond Time Axis Ruler */}
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: '260px 1fr',
            background: '#0A192F',
            borderBottom: '1px solid rgba(255, 255, 255, 0.1)',
            padding: '0.4rem 0.5rem',
            position: 'sticky',
            top: 0,
            zIndex: 10,
          }}
        >
          <div style={{ fontSize: '0.7rem', fontWeight: 700, color: '#94A3B8', textTransform: 'uppercase' }}>
            Service / Operation
          </div>
          <div style={{ position: 'relative', height: '20px', width: '100%' }}>
            {rulerTicks.map((tick, i) => (
              <span
                key={i}
                style={{
                  position: 'absolute',
                  left: `${tick.pct * zoomLevel}%`,
                  fontSize: '0.65rem',
                  fontFamily: 'monospace',
                  color: '#94A3B8',
                  transform: 'translateX(-50%)',
                }}
              >
                {tick.label}
              </span>
            ))}
          </div>
        </div>

        {/* Waterfall Rows */}
        {flatNodes.map((node) => (
          <WaterfallBar
            key={node.spanId}
            node={node}
            isSelected={node.spanId === selectedSpanId}
            isCriticalPath={criticalPathSpanIds.has(node.spanId)}
            zoomLevel={zoomLevel}
            onSelectSpan={onSelectSpan}
          />
        ))}
      </div>
    </div>
  )
}

export default SpanWaterfall
