import { Link } from 'react-router-dom'
import { ALERT_SEVERITY } from '../../lib/derivedAlerts'

function getSeverityClass(severity) {
  if (severity === ALERT_SEVERITY.CRITICAL) {
    return 'status-pill status-pill--error alert-timeline-item__severity'
  }

  return 'status-pill status-pill--warning alert-timeline-item__severity'
}

function AlertTimelineItem({ alert, isSelected = false, onSelect, onDismiss, onKeyDown, itemRef }) {
  const rowClassName = [
    'alert-timeline-item',
    'detected-anomalies__item',
    `alert-timeline-item--${alert.severity}`,
    isSelected ? 'alert-timeline-item--selected' : '',
  ]
    .filter(Boolean)
    .join(' ')

  return (
    <li
      className={rowClassName}
      ref={itemRef}
      onClick={() => onSelect?.(alert)}
      onKeyDown={onKeyDown}
      role="button"
      tabIndex={0}
    >
      <div className="alert-timeline-item__main">
        <div className="alert-timeline-item__topline">
          <span className={getSeverityClass(alert.severity)}>{alert.severity}</span>
          <span className="alert-timeline-item__time">{alert.timeLabel}</span>
        </div>
        <strong className="alert-timeline-item__title">{alert.title}</strong>
        <p className="alert-timeline-item__description">{alert.description}</p>
        <span className="alert-timeline-item__source">Source: {alert.source}</span>
      </div>
      <div className="alert-timeline-item__actions">
        {alert.investigation ? (
          <Link
            className="panel-action alert-timeline-item__action"
            to={alert.investigation.href}
            onClick={(event) => event.stopPropagation()}
          >
            {alert.investigation.label}
          </Link>
        ) : null}
        <button
          className="investigation-toolbar__clear alert-timeline-item__dismiss"
          type="button"
          onClick={(event) => {
            event.stopPropagation()
            onDismiss?.(alert.id)
          }}
        >
          Dismiss
        </button>
      </div>
    </li>
  )
}

export default AlertTimelineItem
