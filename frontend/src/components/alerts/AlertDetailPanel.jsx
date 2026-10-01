import { Link } from 'react-router-dom'
import { EvidenceMetrics, EvidencePanel } from '../analytics/EvidencePrimitives'
import { formatCount } from '../../pages/overviewData'
import { observationTime } from '../../pages/serviceEvidence'
import { evidenceValue, investigationLinks, occurrenceState, ruleScope } from '../../pages/alertInvestigation'

function AlertDetailPanel({ alert, rule, busy, onAction, onClose }) {
  if (!alert) return <EvidencePanel title="Occurrence investigation"><p className="evidence-state">Select an occurrence to inspect its persisted evidence and lifecycle.</p></EvidencePanel>
  const evidence = alert.evidence ?? {}
  const active = ['OPEN', 'ACKNOWLEDGED'].includes(alert.status)
  const acknowledged = alert.acknowledged === true || alert.status === 'ACKNOWLEDGED'
  const traceId = alert.relatedTrace || evidence.traceId
  const spanId = alert.relatedSpan || evidence.spanId
  const service = alert.relatedService || evidence.serviceName
  return <EvidencePanel title={`Occurrence ${alert.alertId ?? '—'}`} note="Persisted deterministic evidence · timestamps in UTC">
    <div className="evidence-actions alert-lifecycle" aria-busy={Boolean(busy)}>
      {active && !acknowledged ? <button type="button" disabled={Boolean(busy) || alert.alertId == null} onClick={() => onAction('acknowledge', alert.alertId)} aria-label={`Acknowledge occurrence ${alert.alertId}; keeps it active`}>Acknowledge · keep active</button> : null}
      {active ? <button type="button" disabled={Boolean(busy) || alert.alertId == null} onClick={() => onAction('resolve', alert.alertId)} aria-label={`Resolve occurrence ${alert.alertId}; close it`}>Resolve · close occurrence</button> : null}
      <button type="button" onClick={onClose}>Close detail</button>
      {busy?.alertId === alert.alertId ? <span role="status">Submitting {busy.kind}…</span> : null}
    </div>
    <EvidenceMetrics items={[
      ['Lifecycle', occurrenceState(alert)], ['Rule', alert.ruleName || '—'], ['Rule ID', formatCount(alert.ruleId)], ['Type', alert.type || '—'],
      ['Configured severity', alert.severity || '—'], ['Configured scope', ruleScope(rule)], ['Last evaluation state', alert.lastEvaluationState || '—'],
      ['First triggered', observationTime(alert.firstTriggeredAt)], ['Last triggered', observationTime(alert.lastTriggeredAt)], ['Evaluated at', observationTime(alert.evaluationTime)],
      ['Acknowledged', alert.acknowledged === true ? 'Yes' : alert.acknowledged === false ? 'No' : '—'], ['Acknowledged at', observationTime(alert.acknowledgedAt)], ['Resolved at', observationTime(alert.resolvedAt)],
    ]} />
    <div className="alert-evidence"><h3>Last match evidence</h3>
      <p>{alert.description || 'Match summary unavailable.'}</p>
      {alert.lastEvaluationState === 'CLEAR' ? <p className="alert-caveat">CLEAR records a non-matching evaluation and does not itself resolve an occurrence. Evidence below is retained from the last match, not the CLEAR evaluation.</p> : null}
      {alert.lastEvaluationState === 'LEGACY_IMPORTED' ? <p className="alert-caveat">Legacy imported occurrence. Trigger/evaluation timestamps use the historical creation time; structured match evidence may be unavailable.</p> : null}
      <EvidenceMetrics items={[
        ['Signal', evidence.metric || '—'], ['Observed value', evidenceValue(evidence.observedValue, evidence.unit)], ['Threshold', evidenceValue(evidence.threshold, evidence.unit)], ['Unit', evidence.unit || '—'],
        ['Samples', formatCount(evidence.sampleCount)], ['Errors', formatCount(evidence.errorCount)], ['Window start', observationTime(evidence.windowStart)], ['Window end', observationTime(evidence.windowEnd)],
        ['Observed at', observationTime(evidence.observedAt)], ['Related service', service || '—'], ['Operation', evidence.operationName || '—'], ['Trace ID', traceId || '—'], ['Span ID', spanId || '—'], ['Observed status', evidence.status || '—'], ['HTTP status', formatCount(evidence.httpStatus)],
      ]} />
      <p className="evidence-note">Threshold rules evaluate SERVER-span samples using ≥ and the configured minimum sample count. TRACE_ERROR matches a trace-level ERROR event. Evidence describes a match, not a causal explanation.</p>
    </div>
    <nav className="evidence-actions" aria-label={`Investigation for occurrence ${alert.alertId ?? 'unknown'}`}>{investigationLinks(alert).map(({ label, href }) => <Link key={label} to={href}>{label}</Link>)}</nav>
    <p className="evidence-note">Service filters match trace entry identity. Service inventory requires selection there. Operation text search matches trace-level fields and is not an exact child-operation cohort.</p>
  </EvidencePanel>
}
export default AlertDetailPanel
