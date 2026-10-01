import PageHeader from '../layout/PageHeader'
import { EvidenceMetrics } from '../analytics/EvidencePrimitives'
import AlertTimeline from './AlertTimeline'
import AlertDetailPanel from './AlertDetailPanel'
import AlertRules from './AlertRules'
import { partitionOccurrences } from '../../pages/alertInvestigation'
import { formatCount } from '../../pages/overviewData'

function AlertsEvidence({ state, websocketStatus, selectedAlertId, onSelect, onRefresh, onAction, onCreateRule }) {
  const { alerts, rules, occurrenceLoading, ruleLoading, occurrenceError, ruleError, busy, actionError, createError, notice } = state
  const groups = partitionOccurrences(alerts ?? [])
  const selected = alerts?.find((alert) => alert.alertId === selectedAlertId)
  const occurrencesAvailable = Array.isArray(alerts) && !occurrenceLoading && !occurrenceError
  const rulesAvailable = Array.isArray(rules) && !ruleLoading && !ruleError
  return <div className="alerts-workspace evidence-workspace alert-investigation">
    <section className="alerts-workspace__header" aria-label="Alerts header"><PageHeader title="Alerts" subtitle="Investigate persisted rule matches, evidence, and occurrence lifecycle." websocketStatus={websocketStatus} isLoading={occurrenceLoading || ruleLoading || Boolean(busy)} onRefresh={onRefresh} statusNote="Authoritative REST snapshots" /></section>
    <p className="evidence-note">{websocketStatus === 'LIVE' ? 'Shared trace live connection is available.' : 'Live trace updates are unavailable or connecting; persisted alert evidence remains independent.'} Alert occurrences refresh on page load, manual Refresh, and successful lifecycle actions.</p>
    <EvidenceMetrics items={[
      ['Active occurrences', occurrencesAvailable ? formatCount(groups.active.length) : '—'], ['Acknowledged · active', occurrencesAvailable ? formatCount(groups.active.filter((a) => a.status === 'ACKNOWLEDGED' || a.acknowledged === true).length) : '—'],
      ['Open · unacknowledged', occurrencesAvailable ? formatCount(groups.active.filter((a) => a.status === 'OPEN' && a.acknowledged === false).length) : '—'], ['Resolved occurrences', occurrencesAvailable ? formatCount(groups.resolved.length) : '—'],
      ['Enabled rules', rulesAvailable ? formatCount(rules.filter((r) => r.enabled === true).length) : '—'], ['Returned rules', rulesAvailable ? formatCount(rules.length) : '—'],
    ]} />
    <p className="evidence-note">Counts use the returned persisted lists. Unknown lifecycle states are excluded from active/resolved counts. Acknowledged occurrences remain active. CLEAR does not automatically resolve an occurrence.</p>
    {actionError ? <p role="alert" className="alert-action-error">Lifecycle action failed. Persisted state has not been inferred; use Refresh to verify or retry the action.</p> : null}
    {notice ? <p role="status" className="alert-notice">{notice}</p> : null}
    {occurrenceLoading ? <p className="evidence-state" role="status" aria-busy="true">Loading alert occurrences…</p> : null}
    {occurrenceError ? <p className="evidence-state" role="alert">Alert occurrences unavailable. Use Refresh to retry.{alerts ? ' Last returned occurrences remain below; counts are unavailable.' : ''}</p> : null}
    {!occurrenceLoading && Array.isArray(alerts) ? <div className="alert-investigation-grid"><div className="alert-occurrence-lists">
      <AlertTimeline title="Active occurrences" alerts={groups.active} selectedAlertId={selectedAlertId} onSelect={onSelect} empty="No active occurrences returned. Acknowledged occurrences are included when unresolved." />
      <details className="alert-history" open={Boolean(selected?.status === 'RESOLVED')}><summary>Resolved history · {formatCount(groups.resolved.length)} returned</summary><AlertTimeline title="Resolved occurrences" alerts={groups.resolved} selectedAlertId={selectedAlertId} onSelect={onSelect} empty="No resolved occurrence history returned." /></details>
      {groups.unknown.length ? <AlertTimeline title="Occurrences with unavailable lifecycle" alerts={groups.unknown} selectedAlertId={selectedAlertId} onSelect={onSelect} /> : null}
    </div><div className="alert-selected"><AlertDetailPanel alert={selected} rule={rules?.find((rule) => rule.id === selected?.ruleId)} busy={busy} onAction={onAction} onClose={() => onSelect(null)} /></div></div> : null}
    <p className="evidence-note">Repeated matches update the same unresolved occurrence and its last-match evidence. After resolution, a later violation creates a new occurrence. Repeat counts and a full evaluation log are not returned.</p>
    <AlertRules rules={rules} loading={ruleLoading} error={ruleError} busy={busy} createError={createError} onCreate={onCreateRule} />
  </div>
}
export default AlertsEvidence
