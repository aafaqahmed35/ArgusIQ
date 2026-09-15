import { useRef } from 'react'
import AlertDetailPanel from './AlertDetailPanel'
import AlertTimelineItem from './AlertTimelineItem'

function AlertTimeline({ alerts, isLoading = false, error = null, selectedAlert = null, onAlertSelect, onAcknowledge, onResolve, onAlertDeselect }) {
  const rowRefs = useRef([])
  const activeCount = alerts.filter((alert) => alert.status !== 'RESOLVED').length
  const hasSelection = Boolean(selectedAlert)

  const handleRowKeyDown = (event, alert, index) => {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault()
      onAlertSelect?.(alert)
    } else if (event.key === 'Escape') {
      event.preventDefault()
      onAlertDeselect?.()
    } else if (event.key === 'ArrowDown' && alerts[index + 1]) {
      event.preventDefault()
      onAlertSelect?.(alerts[index + 1])
      rowRefs.current[index + 1]?.focus()
    } else if (event.key === 'ArrowUp' && alerts[index - 1]) {
      event.preventDefault()
      onAlertSelect?.(alerts[index - 1])
      rowRefs.current[index - 1]?.focus()
    }
  }

  return (
    <section className={`alerts-workspace__body ${hasSelection ? 'alerts-workspace__body--with-detail' : ''}`} aria-label="Alert occurrences">
      <section className={`analytics-panel alert-timeline ${isLoading ? 'analytics-panel--loading' : ''}`} aria-labelledby="alert-timeline-title">
        <div className="analytics-panel__header">
          <div>
            <p className="section-kicker">Evaluated rules</p>
            <h2 id="alert-timeline-title">Alert Timeline</h2>
            <span className="analytics-panel__source-note">{activeCount.toLocaleString()} active · {alerts.length.toLocaleString()} recent</span>
          </div>
        </div>

        {isLoading ? (
          <div className="table-state table-state--skeleton" role="status" aria-busy="true">
            <span className="skeleton-line skeleton-line--wide" /><span className="skeleton-line" /><span className="skeleton-line skeleton-line--wide" />
          </div>
        ) : error && alerts.length === 0 ? (
          <div className="table-state table-state--error" role="alert">Unable to load alert occurrences.</div>
        ) : alerts.length === 0 ? (
          <div className="alert-timeline__empty analytics-empty" role="status">
            <strong>No alert occurrences</strong><span>No configured rule has matched persisted telemetry yet.</span>
          </div>
        ) : (
          <ul className="detected-anomalies__list alert-timeline__list">
            {alerts.map((alert, index) => (
              <AlertTimelineItem alert={alert} isSelected={selectedAlert?.alertId === alert.alertId} itemRef={(element) => { rowRefs.current[index] = element }} key={alert.alertId} onAcknowledge={onAcknowledge} onKeyDown={(event) => handleRowKeyDown(event, alert, index)} onResolve={onResolve} onSelect={onAlertSelect} />
            ))}
          </ul>
        )}
      </section>

      {hasSelection ? <AlertDetailPanel alert={selectedAlert} onAcknowledge={onAcknowledge} onClose={onAlertDeselect} onResolve={onResolve} /> : null}
    </section>
  )
}

export default AlertTimeline
