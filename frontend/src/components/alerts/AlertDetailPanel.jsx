import { Link } from 'react-router-dom'
import { ALERT_SEVERITY } from '../../lib/derivedAlerts'
import { formatDuration } from '../../lib/traceAggregation'

function AlertDetailPanel({ alert, onDismiss, onClose }) {
  if (!alert) {
    return null
  }

  const severityLabel = alert.severity === ALERT_SEVERITY.CRITICAL ? 'Critical' : 'Warning'

  return (
    <section className="analytics-panel alert-detail-panel" aria-labelledby="alert-detail-title">
      <div className="analytics-panel__header">
        <div>
          <p className="section-kicker">Derived alerts</p>
          <h2 id="alert-detail-title">Alert Detail</h2>
        </div>
        <div className="service-group-detail__actions alert-detail-panel__actions">
          {alert.investigation ? (
            <Link className="panel-action alert-detail-panel__action" to={alert.investigation.href}>
              {alert.investigation.label}
            </Link>
          ) : null}
          <button className="investigation-toolbar__clear" type="button" onClick={() => onDismiss?.(alert.id)}>
            Dismiss
          </button>
          <button className="investigation-toolbar__clear" type="button" onClick={onClose}>
            Close
          </button>
        </div>
      </div>

      <dl className="analytics-endpoint-detail__grid">
        <div className="analytics-endpoint-detail__item">
          <dt>Severity</dt>
          <dd>{severityLabel}</dd>
        </div>
        <div className="analytics-endpoint-detail__item">
          <dt>Title</dt>
          <dd>{alert.title}</dd>
        </div>
        <div className="analytics-endpoint-detail__item">
          <dt>When</dt>
          <dd>{alert.timeLabel}</dd>
        </div>
        <div className="analytics-endpoint-detail__item">
          <dt>Source</dt>
          <dd>{alert.source}</dd>
        </div>
        <div className="analytics-endpoint-detail__item">
          <dt>Description</dt>
          <dd>{alert.description}</dd>
        </div>
        {alert.evidence?.averageResponseTime !== undefined ? (
          <div className="analytics-endpoint-detail__item">
            <dt>Average RT</dt>
            <dd>{formatDuration(alert.evidence.averageResponseTime)}</dd>
          </div>
        ) : null}
        {alert.evidence?.p95ResponseTime !== undefined ? (
          <div className="analytics-endpoint-detail__item">
            <dt>P95</dt>
            <dd>{formatDuration(alert.evidence.p95ResponseTime)}</dd>
          </div>
        ) : null}
        {alert.evidence?.slowTraceCount !== undefined ? (
          <div className="analytics-endpoint-detail__item">
            <dt>Slow traces</dt>
            <dd>{alert.evidence.slowTraceCount.toLocaleString()}</dd>
          </div>
        ) : null}
        {alert.evidence?.path ? (
          <div className="analytics-endpoint-detail__item">
            <dt>Path</dt>
            <dd className="analytics-endpoint-detail__path" title={alert.evidence.path}>
              {alert.evidence.path}
            </dd>
          </div>
        ) : null}
      </dl>

      <p className="analytics-endpoint-detail__caption">Derived from live signals on the bounded recent trace window</p>
    </section>
  )
}

export default AlertDetailPanel
