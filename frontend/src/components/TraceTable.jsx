import { getTraceKey } from '../hooks/useTraces'

const DATE_FIELDS = ['timestamp', 'createdAt', 'startTime', 'endTime']
const STATUS_FIELDS = ['status', 'statusCode', 'httpStatus']
const METHOD_FIELDS = ['method', 'httpMethod', 'requestMethod']
const PATH_FIELDS = ['requestUri', 'path', 'endpoint', 'uri', 'url']
const DURATION_FIELDS = ['executionTimeMs', 'responseTime', 'duration', 'latency', 'responseTimeMs']
const SERVICE_FIELDS = ['serviceName', 'service', 'applicationName', 'appName']

function getFieldValue(trace, fields, fallback = '—') {
  const key = fields.find((field) => trace?.[field] !== undefined && trace?.[field] !== null)
  return key ? trace[key] : fallback
}

function formatDate(value) {
  if (!value) {
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
  if (value === '—') {
    return value
  }

  const numericValue = Number(value)

  if (Number.isNaN(numericValue)) {
    return String(value)
  }

  return `${numericValue.toLocaleString()} ms`
}

function getStatusClass(status) {
  const numericStatus = Number(status)

  if (Number.isNaN(numericStatus)) {
    return 'status-pill status-pill--neutral'
  }

  if (numericStatus >= 500) {
    return 'status-pill status-pill--error'
  }

  if (numericStatus >= 400) {
    return 'status-pill status-pill--warning'
  }

  return 'status-pill status-pill--success'
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
            <th scope="col">Time</th>
            <th scope="col">Service</th>
            <th scope="col">Method</th>
            <th scope="col">Path</th>
            <th scope="col">Status</th>
            <th scope="col">Response Time</th>
          </tr>
        </thead>
        <tbody>
          {traces.map((trace) => {
            const status = getFieldValue(trace, STATUS_FIELDS)
            const method = getFieldValue(trace, METHOD_FIELDS)
            const path = getFieldValue(trace, PATH_FIELDS)
            const service = getFieldValue(trace, SERVICE_FIELDS)
            const duration = getFieldValue(trace, DURATION_FIELDS)
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
                <td>{formatDate(timestamp)}</td>
                <td className="cell-strong">{service}</td>
                <td>
                  <span className="method-pill">{method}</span>
                </td>
                <td className="cell-path" title={path}>
                  {path}
                </td>
                <td>
                  <span className={getStatusClass(status)}>{status}</span>
                </td>
                <td>{formatDuration(duration)}</td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}

export default TraceTable
