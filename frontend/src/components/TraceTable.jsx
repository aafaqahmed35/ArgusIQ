import { useState } from 'react'
import { getTraceKey } from '../hooks/useTraces'
import { parseBackendUtcTimestamp } from '../lib/backendDateTime'

const DATE_FIELDS = ['timestamp', 'createdAt', 'startTime', 'endTime']
const STATUS_FIELDS = ['statusCode', 'status', 'httpStatus']
const METHOD_FIELDS = ['httpMethod', 'method', 'requestMethod']
const PATH_FIELDS = ['requestUri', 'path', 'endpoint', 'uri', 'url']
const DURATION_FIELDS = ['durationMs', 'executionTimeMs', 'responseTime', 'duration', 'latency']
const SERVICE_FIELDS = ['serviceName', 'service', 'applicationName', 'appName']
const OPERATION_FIELDS = ['rootSpanName', 'operationName', 'name']
const TRACE_ID_FIELDS = ['traceId', 'id']
const SPAN_COUNT_FIELDS = ['spanCount']

function getFieldValue(trace, fields, fallback = '—') {
  const key = fields.find((field) => trace?.[field] !== undefined && trace?.[field] !== null && trace?.[field] !== '')
  return key ? trace[key] : fallback
}

function formatDate(value) {
  if (!value || value === '—') {
    return '—'
  }

  const date = parseBackendUtcTimestamp(value)

  if (!date) {
    return String(value)
  }

  return new Intl.DateTimeFormat('en', {
    month: 'short',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
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

function getStatusBadge(status) {
  const statusStr = String(status || '').toUpperCase()
  const numericStatus = Number(status)

  if (statusStr === 'ERROR' || (!Number.isNaN(numericStatus) && numericStatus >= 500)) {
    return { label: 'ERROR', className: 'status-pill status-pill--error' }
  }

  if (statusStr === 'UNSET' || (!Number.isNaN(numericStatus) && numericStatus >= 400 && numericStatus < 500)) {
    return { label: statusStr || String(numericStatus), className: 'status-pill status-pill--warning' }
  }

  if (statusStr === 'OK' || (!Number.isNaN(numericStatus) && numericStatus >= 200 && numericStatus < 400)) {
    return { label: statusStr || String(numericStatus), className: 'status-pill status-pill--success' }
  }

  return { label: statusStr || 'UNKNOWN', className: 'status-pill status-pill--neutral' }
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

    navigator.clipboard?.writeText(String(traceId)).then(() => {
      setCopiedTraceId(traceId)
      setTimeout(() => setCopiedTraceId(null), 2000)
    }).catch(() => {})
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
    const backendMessage = error?.response?.data?.message
    return (
      <div className="table-state table-state--error" role="alert">
        {backendMessage || 'Unable to load traces from the backend.'}
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
            <th scope="col">Spans</th>
            <th scope="col">Trace ID</th>
            <th scope="col">Timestamp</th>
            {onTraceSelect ? <th scope="col"><span className="sr-only">Investigation action</span></th> : null}
          </tr>
        </thead>
        <tbody>
          {traces.map((trace) => {
            const rawStatus = getFieldValue(trace, STATUS_FIELDS, '—')
            const duration = getFieldValue(trace, DURATION_FIELDS, '—')
            const badge = getStatusBadge(rawStatus)
            const service = getFieldValue(trace, SERVICE_FIELDS, 'Unknown service')
            const operation = getFieldValue(trace, OPERATION_FIELDS, 'Unknown operation')
            const method = getFieldValue(trace, METHOD_FIELDS, '—')
            const path = getFieldValue(trace, PATH_FIELDS, '—')
            const spanCount = getFieldValue(trace, SPAN_COUNT_FIELDS, '—')
            const traceId = getFieldValue(trace, TRACE_ID_FIELDS, '—')
            const timestamp = getFieldValue(trace, DATE_FIELDS, null)
            const rowKey = getTraceKey(trace)
            const isSelected = selectedTraceKey === rowKey
            const isHighlighted = highlightedTraceKeys.has(rowKey)

            const rowClassName = [
              'trace-table__row',
              isSelected ? 'trace-table__row--selected' : '',
              isHighlighted ? 'trace-table__row--highlight' : '',
            ]
              .filter(Boolean)
              .join(' ')

            return (
              <tr
                className={rowClassName}
                key={rowKey}
                aria-selected={isSelected || undefined}
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
                <td className="trace-table__numeric">{spanCount}</td>
                <td>
                  <div className="trace-table__identity">
                    <code title={String(traceId)}>{formatShortTraceId(traceId)}</code>
                    {traceId !== '—' && (
                      <button
                        type="button"
                        onClick={(e) => handleCopyTraceId(e, traceId)}
                        className="trace-table__copy"
                        aria-label={`Copy trace ID ${traceId}`}
                      >
                        {copiedTraceId === traceId ? 'Copied' : 'Copy'}
                      </button>
                    )}
                  </div>
                </td>
                <td>{formatDate(timestamp)}</td>
                {onTraceSelect ? (
                  <td>
                    <button
                      type="button"
                      className="trace-table__inspect"
                      aria-pressed={isSelected}
                      onClick={() => onTraceSelect(trace)}
                    >
                      {isSelected ? 'Selected' : 'Inspect'}
                    </button>
                  </td>
                ) : null}
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}

export default TraceTable
