import { useEffect, useRef } from 'react'
import TraceDetailSection from './TraceDetailSection'
import TraceMetadataGrid from './TraceMetadataGrid'

const DATE_FIELDS = ['timestamp', 'createdAt', 'startTime', 'endTime']
const STATUS_FIELDS = ['status', 'statusCode', 'httpStatus']
const METHOD_FIELDS = ['method', 'httpMethod', 'requestMethod']
const PATH_FIELDS = ['requestUri', 'path', 'endpoint', 'uri', 'url']
const DURATION_FIELDS = ['executionTimeMs', 'responseTime', 'duration', 'latency', 'responseTimeMs']
const SERVICE_FIELDS = ['serviceName', 'service', 'applicationName', 'appName']
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
  if (value === '—') {
    return value
  }

  const numericValue = Number(value)

  if (Number.isNaN(numericValue)) {
    return String(value)
  }

  return `${numericValue.toLocaleString()} ms`
}

function getTraceMetadata(trace) {
  const timestamp = getFieldValue(trace, DATE_FIELDS)
  const duration = getFieldValue(trace, DURATION_FIELDS)

  return [
    { label: 'Trace ID', value: getFieldValue(trace, TRACE_ID_FIELDS) },
    { label: 'Timestamp', value: formatDate(timestamp) },
    { label: 'Method', value: getFieldValue(trace, METHOD_FIELDS) },
    { label: 'Path', value: getFieldValue(trace, PATH_FIELDS) },
    { label: 'Status', value: getFieldValue(trace, STATUS_FIELDS) },
    { label: 'Duration', value: formatDuration(duration) },
    { label: 'Service', value: getFieldValue(trace, SERVICE_FIELDS) },
  ]
}

function TraceDetailBody({ trace }) {
  const metadata = trace ? getTraceMetadata(trace) : []

  if (!trace) {
    return (
      <div className="trace-detail-panel__empty table-state table-state--rich">
        <strong>Select a trace to inspect</strong>
        <span>Request metadata will appear here when you select a row from the trace table.</span>
      </div>
    )
  }

  return (
    <TraceDetailSection title="Request Metadata">
      <TraceMetadataGrid items={metadata} />
    </TraceDetailSection>
  )
}

function TraceDetailsDrawer({ trace, onClose, variant = 'drawer' }) {
  const drawerPanelRef = useRef(null)
  const isOpen = Boolean(trace)
  const isPanel = variant === 'panel'

  useEffect(() => {
    if (!isOpen || isPanel) {
      return undefined
    }

    function handleDocumentClick(event) {
      const target = event.target

      if (drawerPanelRef.current?.contains(target)) {
        return
      }

      if (target.closest?.('.trace-table__row--interactive')) {
        return
      }

      onClose()
    }

    document.addEventListener('click', handleDocumentClick)

    return () => {
      document.removeEventListener('click', handleDocumentClick)
    }
  }, [isOpen, isPanel, onClose])

  const header = (
    <div className="trace-drawer__header operations-center__header">
      <div>
        <p className="section-kicker">Trace explorer</p>
        <h2 id="trace-drawer-title">Trace Details</h2>
      </div>
      <button
        className="trace-drawer__close"
        type="button"
        aria-label="Close trace details"
        disabled={!isOpen}
        onClick={onClose}
      >
        Close
      </button>
    </div>
  )

  if (isPanel) {
    return (
      <aside
        className="trace-drawer trace-drawer--panel trace-drawer--open"
        aria-label="Trace details"
        aria-labelledby="trace-drawer-title"
      >
        <div className="trace-drawer__panel" ref={drawerPanelRef}>
          {header}
          <TraceDetailBody trace={trace} />
        </div>
      </aside>
    )
  }

  return (
    <div className={`trace-drawer ${isOpen ? 'trace-drawer--open' : ''}`} aria-hidden={!isOpen}>
      <div className="trace-drawer__backdrop" aria-hidden="true" />
      <aside
        className="trace-drawer__panel"
        ref={drawerPanelRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby="trace-drawer-title"
      >
        {header}
        <TraceDetailBody trace={trace} />
      </aside>
    </div>
  )
}

export default TraceDetailsDrawer
