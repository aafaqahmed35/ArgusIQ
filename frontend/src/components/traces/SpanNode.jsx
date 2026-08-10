import React, { memo } from 'react'

function getStatusColor(statusCode) {
  const code = (statusCode || '').toUpperCase()
  if (code === 'ERROR' || code === '5XX' || code === '500') return '#EF4444'
  if (code === 'WARN' || code === 'UNSET' || code === '4XX' || code === '400') return '#F59E0B'
  return '#10B981' // Success Green
}

const SpanNode = memo(function SpanNode({
  node,
  isSelected,
  isCriticalPath,
  isMatchingSearch,
  onSelectNode,
  onToggleExpand,
}) {
  const {
    spanId,
    name,
    serviceName,
    kind,
    durationMs,
    statusCode,
    depth,
    hasChildren,
    isExpanded,
    relativeDurationPct,
  } = node

  const statusColor = getStatusColor(statusCode)
  const isGoldBorder = isCriticalPath

  const handleRowClick = (e) => {
    e.stopPropagation()
    onSelectNode?.(node)
  }

  const handleToggleClick = (e) => {
    e.stopPropagation()
    onToggleExpand?.(spanId)
  }

  return (
    <div
      onClick={handleRowClick}
      className={`span-node-row ${isSelected ? 'is-selected' : ''} ${isGoldBorder ? 'is-critical-path' : ''} ${isMatchingSearch ? 'is-search-match' : ''}`}
      style={{
        display: 'flex',
        alignItems: 'center',
        padding: '0.4rem 0.75rem',
        paddingLeft: `${depth * 1.5 + 0.75}rem`,
        background: isSelected
          ? 'rgba(56, 189, 248, 0.15)'
          : isCriticalPath
          ? 'rgba(212, 175, 55, 0.08)'
          : isMatchingSearch
          ? 'rgba(245, 158, 11, 0.12)'
          : 'transparent',
        borderLeft: isCriticalPath
          ? '3px solid #D4AF37'
          : isSelected
          ? '3px solid #38bdf8'
          : '3px solid transparent',
        borderBottom: '1px solid rgba(255, 255, 255, 0.04)',
        cursor: 'pointer',
        transition: 'background 0.15s ease',
        userSelect: 'none',
        gap: '0.5rem',
      }}
    >
      {/* Expand/Collapse Toggle Button */}
      <span
        onClick={handleToggleClick}
        style={{
          width: '18px',
          height: '18px',
          display: 'inline-flex',
          alignItems: 'center',
          justifyContent: 'center',
          borderRadius: '3px',
          background: hasChildren ? 'rgba(255, 255, 255, 0.08)' : 'transparent',
          color: '#94A3B8',
          fontSize: '0.75rem',
          fontWeight: 'bold',
          visibility: hasChildren ? 'visible' : 'hidden',
          cursor: 'pointer',
        }}
      >
        {isExpanded ? '▼' : '▶'}
      </span>

      {/* Status Dot */}
      <span
        style={{
          width: '8px',
          height: '8px',
          borderRadius: '50%',
          backgroundColor: statusColor,
          boxShadow: `0 0 6px ${statusColor}`,
          flexShrink: 0,
        }}
      />

      {/* Service Pill */}
      <span
        style={{
          fontSize: '0.7rem',
          fontWeight: 600,
          color: isCriticalPath ? '#D4AF37' : '#38bdf8',
          background: 'rgba(15, 23, 42, 0.8)',
          border: `1px solid ${isCriticalPath ? 'rgba(212, 175, 55, 0.4)' : 'rgba(56, 189, 248, 0.2)'}`,
          padding: '1px 6px',
          borderRadius: '4px',
          flexShrink: 0,
        }}
      >
        {serviceName}
      </span>

      {/* Operation Name */}
      <span
        style={{
          fontSize: '0.85rem',
          fontWeight: isSelected ? 700 : 500,
          color: '#F5F7FA',
          whiteSpace: 'nowrap',
          overflow: 'hidden',
          textOverflow: 'ellipsis',
          flexGrow: 1,
        }}
        title={name}
      >
        {name}
      </span>

      {/* Span Kind Badge */}
      <span
        style={{
          fontSize: '0.65rem',
          color: '#94A3B8',
          background: 'rgba(255, 255, 255, 0.05)',
          padding: '1px 4px',
          borderRadius: '3px',
          textTransform: 'uppercase',
          flexShrink: 0,
        }}
      >
        {kind}
      </span>

      {/* Relative Duration Bar */}
      <div
        style={{
          width: '70px',
          height: '4px',
          background: 'rgba(255, 255, 255, 0.1)',
          borderRadius: '2px',
          overflow: 'hidden',
          flexShrink: 0,
        }}
        title={`${relativeDurationPct.toFixed(1)}% of trace duration`}
      >
        <div
          style={{
            width: `${relativeDurationPct}%`,
            height: '100%',
            background: isCriticalPath ? '#D4AF37' : statusColor,
            borderRadius: '2px',
          }}
        />
      </div>

      {/* Duration Badge */}
      <span
        style={{
          fontSize: '0.75rem',
          fontWeight: 600,
          color: isCriticalPath ? '#D4AF37' : '#F5F7FA',
          fontFamily: 'monospace',
          flexShrink: 0,
          minWidth: '60px',
          textAlign: 'right',
        }}
      >
        {durationMs} ms
      </span>
    </div>
  )
})

export default SpanNode
