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
      <div className="trace-visualization-empty">
        No spans available to render waterfall timeline.
      </div>
    )
  }

  return (
    <div className="span-waterfall-container">
      <div className="trace-visualization-toolbar">
        <div>
          Wall-clock trace duration: <strong>{totalDurationMs} ms</strong> ({spanMap.size} spans)
        </div>
        <div className="trace-visualization-toolbar__actions">
          <button className="trace-visualization-button" type="button" onClick={handleZoomOut} aria-label="Zoom waterfall out">
            −
          </button>
          <span className="trace-visualization-toolbar__zoom">
            {Math.round(zoomLevel * 100)}%
          </span>
          <button className="trace-visualization-button" type="button" onClick={handleZoomIn} aria-label="Zoom waterfall in">
            +
          </button>
          <button className="trace-visualization-button" type="button" onClick={handleResetZoom}>
            Reset
          </button>
        </div>
      </div>

      <div className="span-waterfall-container__rows">
        <div className="waterfall-ruler">
          <div className="waterfall-ruler__label">
            Service / Operation
          </div>
          <div className="waterfall-ruler__ticks">
            {rulerTicks.map((tick, i) => (
              <span
                key={i}
                style={{ '--waterfall-tick': `${tick.pct * zoomLevel}%` }}
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
