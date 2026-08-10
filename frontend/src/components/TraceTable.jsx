import { useState } from 'react'
import { getTraceKey } from '../hooks/useTraces'

const DATE_FIELDS = ['timestamp', 'createdAt', 'startTime', 'endTime']
const STATUS_FIELDS = ['statusCode', 'status', 'httpStatus']
const METHOD_FIELDS = ['httpMethod', 'method', 'requestMethod']
const PATH_FIELDS = ['requestUri', 'path', 'endpoint', 'uri', 'url']
const DURATION_FIELDS = ['durationMs', 'executionTimeMs', 'responseTime', 'duration', 'latency']
const SERVICE_FIELDS = ['serviceName', 'service', 'applicationName', 'appName']
const OPERATION_FIELDS = ['rootSpanName', 'operationName', 'name']
const TRACE_ID_FIELDS = ['traceId', 'id']

function getFieldValue(trace, fields, fallback = '—') {
  const key = fields.find((field) => trace?.[field] !== undefined && trace?.[field] !== null && trace?.[field] !== '')
  return key ? trace[key] : fallback
}

function formatDate(value) {
  if (!value || value === '—') {
    return '—'
  }

  const date = new Date(value)

  if (Number.isNaN(date.getTime())) {
    return String(value)
  }

  return new Intl.DateTimeFormat('en', {
    dateStyle: 'medium',
    timeStyle: 'medium',
  }).format(date)
}

function formatDuration(value) {
  if (value === '—' || value === null || value === undefined) {
    return '—'
  }

  const numericValue = Number(value)

  if (Number.isNaN(numericValue)) {
    return String(value)
  }

  return `${numericValue.toLocaleString()} ms`
}

function getStatusBadge(status, durationMs) {
  const statusStr = String(status || '').toUpperCase()
  const numericStatus = Number(status)

  if (statusStr === 'ERROR' || (!Number.isNaN(numericStatus) && numericStatus >= 500)) {
    return { label: 'ERROR', className: 'status-pill status-pill--error' }
  }

  if (
    statusStr === 'UNSET' ||
    (!Number.isNaN(numericStatus) && numericStatus >= 400 && numericStatus < 500) ||
    (Number(durationMs) >= 1000)
  ) {
    return { label: statusStr === 'UNSET' ? 'WARN' : statusStr || 'WARN', className: 'status-pill status-pill--warning' }
  }

  return { label: statusStr === 'OK' ? 'OK' : statusStr || 'OK', className: 'status-pill status-pill--success' }
}

function formatShortTraceId(traceId) {
  if (!traceId || traceId === '—') return '—'
  const str = String(traceId)
  if (str.length <= 12) return str
  return `${str.slice(0, 6)}...${str.slice(-4)}`
}

function TraceTable({
  traces,
  isLoading,
  error,
  onTraceSelect,
  selectedTraceKey = null,
  highlightedTraceKeys = new Set(),
  emptyTitle = 'No traces available',
  emptyMessage = 'Trace records will appear here as soon as the frontend receives telemetry.',
}) {
  const [copiedTraceId, setCopiedTraceId] = useState(null)

  const handleCopyTraceId = (e, traceId) => {
    e.stopPropagation()
    if (!traceId || traceId === '—') return

    navigator.clipboard.writeText(String(traceId))
    setCopiedTraceId(traceId)
    setTimeout(() => setCopiedTraceId(null), 2000)
  }

  if (isLoading) {
    return (
      <div className="table-state table-state--skeleton" role="status" aria-busy="true">
        <span className="skeleton-line skeleton-line--wide" />
        <span className="skeleton-line" />
        <span className="skeleton-line skeleton-line--wide" />
        <span className="skeleton-line skeleton-line--short" />
      </div>
    )
  }

  if (error) {
    return (
      <div className="table-state table-state--error" role="alert">
        Unable to load traces from the backend.
      </div>
    )
  }

  if (traces.length === 0) {
    return (
      <div className="table-state table-state--empty table-state--rich">
        <strong>{emptyTitle}</strong>
        <span>{emptyMessage}</span>
      </div>
    )
  }

  return (
    <div className="table-shell">
      <table className="trace-table">
        <thead>
          <tr>
            <th scope="col">Status</th>
            <th scope="col">Service</th>
            <th scope="col">Operation</th>
            <th scope="col">Method</th>
            <th scope="col">Endpoint</th>
            <th scope="col">Duration</th>
            <th scope="col">Trace ID</th>
            <th scope="col">Timestamp</th>
          </tr>
        </thead>
        <tbody>
          {traces.map((trace) => {
            const rawStatus = getFieldValue(trace, STATUS_FIELDS, 'OK')
            const duration = getFieldValue(trace, DURATION_FIELDS, 0)
            const badge = getStatusBadge(rawStatus, duration)
            const service = getFieldValue(trace, SERVICE_FIELDS, 'AtlasBank')
            const operation = getFieldValue(trace, OPERATION_FIELDS, 'HTTP Request')
            const method = getFieldValue(trace, METHOD_FIELDS, 'OTLP')
            const path = getFieldValue(trace, PATH_FIELDS, '/')
            const traceId = getFieldValue(trace, TRACE_ID_FIELDS, '—')
            const timestamp = getFieldValue(trace, DATE_FIELDS, null)
            const rowKey = getTraceKey(trace)
            const isSelected = selectedTraceKey === rowKey
            const isHighlighted = highlightedTraceKeys.has(rowKey)

            const rowClassName = [
              'trace-table__row',
              onTraceSelect ? 'trace-table__row--interactive' : '',
              isSelected ? 'trace-table__row--selected' : '',
              isHighlighted ? 'trace-table__row--highlight' : '',
            ]
              .filter(Boolean)
              .join(' ')

            const handleTraceSelect = () => {
              onTraceSelect?.(trace)
            }

            const handleTraceSelectKeyDown = (event) => {
              if (event.key === 'Enter' || event.key === ' ') {
                event.preventDefault()
                handleTraceSelect()
              }
            }

            return (
              <tr
                className={rowClassName}
                key={rowKey}
                onClick={handleTraceSelect}
                onKeyDown={handleTraceSelectKeyDown}
                role={onTraceSelect ? 'button' : undefined}
                tabIndex={onTraceSelect ? 0 : undefined}
              >
                <td>
                  <span className={badge.className}>{badge.label}</span>
                </td>
                <td className="cell-strong">{service}</td>
                <td className="cell-strong" title={operation}>
                  {operation}
                </td>
                <td>
                  <span className="method-pill">{method}</span>
                </td>
                <td className="cell-path" title={path}>
                  {path}
                </td>
                <td>{formatDuration(duration)}</td>
                <td>
                  <div style={{ display: 'inline-flex', alignItems: 'center', gap: '0.4rem' }}>
                    <code title={String(traceId)}>{formatShortTraceId(traceId)}</code>
                    {traceId !== '—' && (
                      <button
                        type="button"
                        onClick={(e) => handleCopyTraceId(e, traceId)}
                        title="Copy Trace ID"
                        style={{
                          background: 'transparent',
                          border: 'none',
                          color: copiedTraceId === traceId ? '#10b981' : '#94a3b8',
                          cursor: 'pointer',
                          padding: '2px 4px',
                          fontSize: '0.75rem',
                        }}
                      >
                        {copiedTraceId === traceId ? '✓' : '📋'}
                      </button>
                    )}
                  </div>
                </td>
                <td>{formatDate(timestamp)}</td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}

export default TraceTable
