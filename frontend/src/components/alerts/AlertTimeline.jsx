import { useRef } from 'react'
import AlertDetailPanel from './AlertDetailPanel'
import AlertTimelineItem from './AlertTimelineItem'

function AlertTimeline({
  alerts,
  isLoading = false,
  error = null,
  selectedAlert = null,
  onAlertSelect,
  onAlertDismiss,
  onAlertDeselect,
}) {
  const rowRefs = useRef([])

  const sourceNote = `${alerts.length.toLocaleString()} active · Derived from live signals`
  const hasSelection = Boolean(selectedAlert)

  const focusRow = (index) => {
    rowRefs.current[index]?.focus()
  }

  const handleRowKeyDown = (event, alert, index) => {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault()
      onAlertSelect?.(alert)
      return
    }

    if (event.key === 'Escape') {
      event.preventDefault()
      onAlertDeselect?.()
      return
    }

    if (event.key === 'ArrowDown') {
      event.preventDefault()
      const nextAlert = alerts[index + 1]

      if (nextAlert) {
        onAlertSelect?.(nextAlert)
        focusRow(index + 1)
      }

      return
    }

    if (event.key === 'ArrowUp') {
      event.preventDefault()
      const previousAlert = alerts[index - 1]

      if (previousAlert) {
        onAlertSelect?.(previousAlert)
        focusRow(index - 1)
      }
    }
  }

  return (
    <section
      className={`alerts-workspace__body ${hasSelection ? 'alerts-workspace__body--with-detail' : ''}`}
      aria-label="Derived alerts"
    >
      <section
        className={`analytics-panel alert-timeline ${isLoading ? 'analytics-panel--loading' : ''}`}
        aria-labelledby="alert-timeline-title"
      >
        <div className="analytics-panel__header">
          <div>
            <p className="section-kicker">Derived alerts</p>
            <h2 id="alert-timeline-title">Alert Timeline</h2>
            <span className="analytics-panel__source-note">{sourceNote}</span>
          </div>
        </div>

        {isLoading ? (
          <div className="table-state table-state--skeleton" role="status" aria-busy="true">
            <span className="skeleton-line skeleton-line--wide" />
            <span className="skeleton-line" />
            <span className="skeleton-line skeleton-line--wide" />
            <span className="skeleton-line skeleton-line--short" />
          </div>
        ) : error && alerts.length === 0 ? (
          <div className="table-state table-state--error" role="alert">
            Unable to load traces from the backend.
          </div>
        ) : alerts.length === 0 ? (
          <div className="alert-timeline__empty analytics-empty" role="status">
            <strong>No active derived alerts</strong>
            <span>Derived signals from the recent trace window and connection state are within normal bounds.</span>
            <small>Alerts update live as telemetry and connection state change.</small>
          </div>
        ) : (
          <ul className="detected-anomalies__list alert-timeline__list">
            {alerts.map((alert, index) => (
              <AlertTimelineItem
                alert={alert}
                isSelected={selectedAlert?.id === alert.id}
                itemRef={(element) => {
                  rowRefs.current[index] = element
                }}
                key={alert.id}
                onDismiss={onAlertDismiss}
                onKeyDown={(event) => handleRowKeyDown(event, alert, index)}
                onSelect={onAlertSelect}
              />
            ))}
          </ul>
        )}
      </section>

      {hasSelection ? (
        <AlertDetailPanel alert={selectedAlert} onClose={onAlertDeselect} onDismiss={onAlertDismiss} />
      ) : null}
    </section>
  )
}

export default AlertTimeline
