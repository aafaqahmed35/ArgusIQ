import { memo } from 'react'

function statusTone(statusCode) {
  const code = String(statusCode || 'UNKNOWN').toUpperCase()
  if (code === 'ERROR' || code === '5XX' || code === '500') return 'error'
  if (code === 'WARN' || code === 'UNSET' || code === '4XX' || code === '400') return 'warning'
  if (code === 'OK') return 'success'
  return 'neutral'
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

  const tone = statusTone(statusCode)
  const rowClassName = [
    'span-node-row',
    isSelected ? 'is-selected' : '',
    isCriticalPath ? 'is-critical-path' : '',
    isMatchingSearch ? 'is-search-match' : '',
  ].filter(Boolean).join(' ')

  return (
    <div className={rowClassName} style={{ '--span-depth': depth, '--span-duration': `${relativeDurationPct}%` }}>
      {hasChildren ? (
        <button
          type="button"
          className="span-node-row__toggle"
          aria-label={`${isExpanded ? 'Collapse' : 'Expand'} ${name}`}
          aria-expanded={isExpanded}
          onClick={() => onToggleExpand?.(spanId)}
        >
          {isExpanded ? '−' : '+'}
        </button>
      ) : <span className="span-node-row__toggle-placeholder" aria-hidden="true" />}

      <button
        type="button"
        className="span-node-row__select"
        aria-pressed={isSelected}
        onClick={() => onSelectNode?.(node)}
      >
        <span className={`span-status-dot span-status-dot--${tone}`} aria-hidden="true" />
        <span className="sr-only">Status {statusCode || 'unknown'}. </span>
        <span className="span-node-row__service">{serviceName}</span>
        <span className="span-node-row__name" title={name}>{name}</span>
        <span className="span-node-row__kind">{kind}</span>
        <span className="span-node-row__duration-track" aria-hidden="true">
          <span />
        </span>
        <span className="span-node-row__duration">{durationMs} ms</span>
      </button>
    </div>
  )
})

export default SpanNode
