import { memo } from 'react'

function statusTone(statusCode) {
  const code = String(statusCode || 'UNKNOWN').toUpperCase()
  if (code === 'ERROR' || code === '5XX' || code === '500') return 'error'
  if (code === 'WARN' || code === 'UNSET' || code === '4XX' || code === '400') return 'warning'
  if (code === 'OK') return 'success'
  return 'neutral'
}

const WaterfallBar = memo(function WaterfallBar({ node, isSelected, isCriticalPath, zoomLevel = 1, onSelectSpan }) {
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
  const endOffsetMs = startOffsetMs + durationMs
  const rowClassName = [
    'waterfall-row',
    `waterfall-row--${statusTone(statusCode)}`,
    isSelected ? 'is-selected' : '',
    isCriticalPath ? 'is-critical-path' : '',
  ].filter(Boolean).join(' ')

  return (
    <button
      type="button"
      className={rowClassName}
      aria-pressed={isSelected}
      onClick={() => onSelectSpan?.(node)}
      style={{
        '--span-depth': depth,
        '--waterfall-left': `${relativeStartPct * zoomLevel}%`,
        '--waterfall-width': `${Math.max(0.75, relativeDurationPct * zoomLevel)}%`,
        '--waterfall-label-left': `calc(${relativeStartPct * zoomLevel}% + ${Math.max(1, relativeDurationPct * zoomLevel)}% + 6px)`,
      }}
    >
      <span className="waterfall-row__identity" title={`${serviceName}: ${name}`}>
        <span className="waterfall-row__service">{serviceName}</span>
        <span className="waterfall-row__name">{name}</span>
        <span className={`waterfall-row__status waterfall-row__status--${statusTone(statusCode)}`}>
          {statusCode || 'UNKNOWN'}
        </span>
      </span>

      <span className="waterfall-row__track">
        <span
          className="waterfall-row__bar"
          title={`${name}\nStart: +${startOffsetMs} ms\nDuration: ${durationMs} ms\nEnd: +${endOffsetMs} ms`}
        >
          {durationMs > 10 ? `${durationMs} ms` : ''}
        </span>
        <span className="waterfall-row__timing">+{startOffsetMs} ms ({durationMs} ms)</span>
      </span>
    </button>
  )
})

export default WaterfallBar
