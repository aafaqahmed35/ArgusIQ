import { parseBackendUtcTimestamp } from '../../lib/backendDateTime'

function formatTimestamp(value) {
  if (!value) return '—'
  const date = parseBackendUtcTimestamp(value)
  return date ? date.toLocaleString() : String(value)
}

function formatEvidenceValue(value, unit) {
  if (value === null || value === undefined) return '—'
  return `${Number(value).toLocaleString()}${unit === 'PERCENT' ? '%' : unit === 'MILLISECONDS' ? ' ms' : ''}`
}

function Detail({ label, children }) {
  if (children === null || children === undefined || children === '—') return null
  return <div className="analytics-endpoint-detail__item"><dt>{label}</dt><dd>{children}</dd></div>
}

function AlertDetailPanel({ alert, onAcknowledge, onResolve, onClose }) {
  if (!alert) return null
  const evidence = alert.evidence ?? {}
  const isResolved = alert.status === 'RESOLVED'

  return (
    <section className="analytics-panel alert-detail-panel" aria-labelledby="alert-detail-title">
      <div className="analytics-panel__header">
        <div><p className="section-kicker">Persisted occurrence</p><h2 id="alert-detail-title">Alert Detail</h2></div>
        <div className="service-group-detail__actions alert-detail-panel__actions">
          {!alert.acknowledged && !isResolved ? <button className="investigation-toolbar__clear" type="button" onClick={() => onAcknowledge?.(alert.alertId)}>Acknowledge</button> : null}
          {!isResolved ? <button className="investigation-toolbar__clear" type="button" onClick={() => onResolve?.(alert.alertId)}>Resolve</button> : null}
          <button className="investigation-toolbar__clear" type="button" onClick={onClose}>Close</button>
        </div>
      </div>

      <dl className="analytics-endpoint-detail__grid">
        <Detail label="Rule">{alert.ruleName}</Detail><Detail label="Severity">{alert.severity}</Detail><Detail label="Status">{alert.status}</Detail>
        <Detail label="Last evaluation">{alert.lastEvaluationState}</Detail><Detail label="First triggered">{formatTimestamp(alert.firstTriggeredAt)}</Detail><Detail label="Last triggered">{formatTimestamp(alert.lastTriggeredAt)}</Detail>
        <Detail label="Signal">{evidence.metric}</Detail><Detail label="Observed">{formatEvidenceValue(evidence.observedValue, evidence.unit)}</Detail><Detail label="Threshold">{formatEvidenceValue(evidence.threshold, evidence.unit)}</Detail>
        <Detail label="Samples">{evidence.sampleCount}</Detail><Detail label="Errors">{evidence.errorCount}</Detail><Detail label="Window start">{formatTimestamp(evidence.windowStart)}</Detail><Detail label="Window end">{formatTimestamp(evidence.windowEnd)}</Detail>
        <Detail label="Observed at">{formatTimestamp(evidence.observedAt)}</Detail>
        <Detail label="Service">{evidence.serviceName}</Detail><Detail label="Operation">{evidence.operationName}</Detail><Detail label="Trace">{evidence.traceId}</Detail><Detail label="Status observed">{evidence.status}</Detail><Detail label="HTTP status">{evidence.httpStatus}</Detail>
        <Detail label="Summary">{alert.description}</Detail>
      </dl>

      <p className="analytics-endpoint-detail__caption">Structured evidence from deterministic evaluation of persisted telemetry.</p>
    </section>
  )
}

export default AlertDetailPanel
