import React, { memo } from 'react'

function getStatusColor(statusCode) {
  const code = (statusCode || '').toUpperCase()
  if (code === 'ERROR' || code === '5XX' || code === '500') return '#EF4444'
  if (code === 'WARN' || code === 'UNSET' || code === '4XX' || code === '400') return '#F59E0B'
  return '#10B981'
}

const WaterfallBar = memo(function WaterfallBar({
  node,
  isSelected,
  isCriticalPath,
  zoomLevel = 1,
  onSelectSpan,
}) {
  const {
    name,
    serviceName,
    durationMs,
    startOffsetMs,
    relativeStartPct,
    relativeDurationPct,
    statusCode,
    depth,
  } = node

  const statusColor = getStatusColor(statusCode)
  const isGold = isCriticalPath

  const endOffsetMs = startOffsetMs + durationMs

  return (
    <div
      onClick={() => onSelectSpan?.(node)}
      className={`waterfall-row ${isSelected ? 'is-selected' : ''}`}
      style={{
        display: 'grid',
        gridTemplateColumns: '260px 1fr',
        alignItems: 'center',
        padding: '0.35rem 0.5rem',
        borderBottom: '1px solid rgba(255, 255, 255, 0.04)',
        background: isSelected
          ? 'rgba(56, 189, 248, 0.12)'
          : isGold
          ? 'rgba(212, 175, 55, 0.05)'
          : 'transparent',
        cursor: 'pointer',
        fontSize: '0.8rem',
      }}
    >
      {/* Sticky Left Label Column */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: '0.5rem',
          paddingLeft: `${depth * 0.75}rem`,
          overflow: 'hidden',
          whiteSpace: 'nowrap',
          textOverflow: 'ellipsis',
          paddingRight: '0.5rem',
        }}
        title={`${serviceName}: ${name}`}
      >
        <span
          style={{
            fontSize: '0.65rem',
            fontWeight: 700,
            color: isGold ? '#D4AF37' : '#38bdf8',
            background: 'rgba(15, 23, 42, 0.9)',
            border: `1px solid ${isGold ? 'rgba(212, 175, 55, 0.4)' : 'rgba(56, 189, 248, 0.25)'}`,
            padding: '1px 5px',
            borderRadius: '3px',
            flexShrink: 0,
          }}
        >
          {serviceName}
        </span>
        <span style={{ color: '#F5F7FA', overflow: 'hidden', textOverflow: 'ellipsis' }}>{name}</span>
      </div>

      {/* Proportional Waterfall Bar Track */}
      <div
        style={{
          position: 'relative',
          height: '24px',
          display: 'flex',
          alignItems: 'center',
          width: '100%',
          overflow: 'hidden',
        }}
      >
        {/* Waterfall Bar Span */}
        <div
          title={`${name}\nStart: +${startOffsetMs} ms\nDuration: ${durationMs} ms\nEnd: +${endOffsetMs} ms`}
          style={{
            position: 'absolute',
            left: `${relativeStartPct * zoomLevel}%`,
            width: `${Math.max(0.75, relativeDurationPct * zoomLevel)}%`,
            height: '16px',
            backgroundColor: isGold ? '#D4AF37' : statusColor,
            borderRadius: '3px',
            boxShadow: isGold ? '0 0 8px rgba(212, 175, 55, 0.6)' : `0 0 6px ${statusColor}`,
            display: 'flex',
            alignItems: 'center',
            padding: '0 4px',
            transition: 'width 0.2s ease, left 0.2s ease',
          }}
        >
          <span
            style={{
              fontSize: '0.65rem',
              fontWeight: 700,
              color: isGold ? '#07131F' : '#07131F',
              fontFamily: 'monospace',
              whiteSpace: 'nowrap',
              overflow: 'hidden',
            }}
          >
            {durationMs > 10 ? `${durationMs} ms` : ''}
          </span>
        </div>

        {/* Start/End Millisecond Text Tag */}
        <span
          style={{
            position: 'absolute',
            left: `calc(${relativeStartPct * zoomLevel}% + ${Math.max(1, relativeDurationPct * zoomLevel)}% + 6px)`,
            fontSize: '0.7rem',
            color: isGold ? '#D4AF37' : '#94A3B8',
            fontFamily: 'monospace',
            whiteSpace: 'nowrap',
          }}
        >
          +{startOffsetMs} ms ({durationMs} ms)
        </span>
      </div>
    </div>
  )
})

export default WaterfallBar
