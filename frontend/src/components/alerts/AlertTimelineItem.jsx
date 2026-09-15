import { Link } from 'react-router-dom'
import { parseBackendUtcTimestamp } from '../../lib/backendDateTime'

function severityClass(severity) {
  return severity?.toUpperCase() === 'CRITICAL' ? 'status-pill status-pill--error alert-timeline-item__severity' : 'status-pill status-pill--warning alert-timeline-item__severity'
}

function formatTimestamp(value) {
  if (!value) return '—'
  const date = parseBackendUtcTimestamp(value)
  return date ? new Intl.DateTimeFormat('en', { dateStyle: 'medium', timeStyle: 'medium' }).format(date) : String(value)
}

function AlertTimelineItem({ alert, isSelected = false, onSelect, onAcknowledge, onResolve, onKeyDown, itemRef }) {
  const isResolved = alert.status === 'RESOLVED'
  const isAcknowledged = alert.status === 'ACKNOWLEDGED'

  return (
    <li className={`alert-timeline-item detected-anomalies__item alert-timeline-item--${alert.severity?.toLowerCase()} ${isSelected ? 'alert-timeline-item--selected' : ''}`} ref={itemRef} onClick={() => onSelect?.(alert)} onKeyDown={onKeyDown} role="button" tabIndex={0}>
      <div className="alert-timeline-item__main">
        <div className="alert-timeline-item__topline">
          <span className={severityClass(alert.severity)}>{alert.severity}</span>
          <span className="status-pill">{alert.status}</span>
          <span className="alert-timeline-item__time">{formatTimestamp(alert.lastTriggeredAt)}</span>
        </div>
        <strong className="alert-timeline-item__title">{alert.ruleName}</strong>
        <p className="alert-timeline-item__description">{alert.description}</p>
        <span className="alert-timeline-item__source">Evidence: persisted telemetry · {alert.evidence?.metric}</span>
      </div>
      <div className="alert-timeline-item__actions">
        {alert.relatedTrace ? <Link className="panel-action alert-timeline-item__action" to="/traces" onClick={(event) => event.stopPropagation()}>View traces →</Link> : null}
        {!isAcknowledged && !isResolved ? <button className="investigation-toolbar__clear" type="button" onClick={(event) => { event.stopPropagation(); onAcknowledge?.(alert.alertId) }}>Acknowledge</button> : null}
        {!isResolved ? <button className="investigation-toolbar__clear alert-timeline-item__dismiss" type="button" onClick={(event) => { event.stopPropagation(); onResolve?.(alert.alertId) }}>Resolve</button> : null}
      </div>
    </li>
  )
}

export default AlertTimelineItem
