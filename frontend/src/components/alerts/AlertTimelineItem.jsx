import { evidenceValue, occurrenceState } from '../../pages/alertInvestigation'
import { observationTime } from '../../pages/serviceEvidence'

function AlertTimelineItem({ alert, isSelected, onSelect }) {
  return <tr>
    <th scope="row" className="evidence-name"><button type="button" aria-pressed={isSelected} aria-label={`Inspect occurrence ${alert.alertId ?? 'unknown'}`} onClick={() => onSelect(alert.alertId)}>{alert.ruleName || alert.title || 'Rule name unavailable'}</button><small>Occurrence {alert.alertId ?? '—'} · {alert.type || 'Type unavailable'}</small></th>
    <td>{occurrenceState(alert)}<small className="alert-meta">Configured severity: {alert.severity || '—'}</small></td>
    <td className="evidence-name">{alert.relatedService || alert.evidence?.serviceName || 'Service not specified'}</td>
    <td>{alert.evidence?.metric || 'Signal unavailable'}<small className="alert-meta">Observed {evidenceValue(alert.evidence?.observedValue, alert.evidence?.unit)} · threshold {evidenceValue(alert.evidence?.threshold, alert.evidence?.unit)}</small><small className="alert-meta">Last evaluation: {alert.lastEvaluationState || '—'}</small></td>
    <td>{observationTime(alert.lastTriggeredAt)}</td>
  </tr>
}
export default AlertTimelineItem
