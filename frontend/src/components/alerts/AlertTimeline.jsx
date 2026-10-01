import { EvidencePanel } from '../analytics/EvidencePrimitives'
import AlertTimelineItem from './AlertTimelineItem'

function AlertTimeline({ title, alerts, selectedAlertId, onSelect, empty }) {
  return <EvidencePanel title={title} note="Persisted occurrences · backend ordering by last trigger">
    {alerts.length ? <div className="evidence-scroll" tabIndex={0} role="region" aria-label={title}><table className="evidence-table"><thead><tr><th scope="col">Rule / occurrence</th><th scope="col">Lifecycle</th><th scope="col">Related service</th><th scope="col">Last match evidence</th><th scope="col">Last triggered (UTC)</th></tr></thead><tbody>{alerts.map((alert, index) => <AlertTimelineItem key={alert.alertId ?? index} alert={alert} isSelected={selectedAlertId === alert.alertId} onSelect={onSelect} />)}</tbody></table></div> : <p className="evidence-state" role="status">{empty}</p>}
  </EvidencePanel>
}
export default AlertTimeline
