import { useState } from 'react'
import { EvidencePanel } from '../analytics/EvidencePrimitives'
import { buildRuleRequest, evidenceValue, ruleScope } from '../../pages/alertInvestigation'
import { formatCount } from '../../pages/overviewData'
import { observationTime } from '../../pages/serviceEvidence'

export function RuleCreateForm({ busy, error, onCreate }) {
  const [draft, setDraft] = useState({ name: '', serviceName: '', type: 'TRACE_ERROR', severity: 'WARNING', enabled: true, threshold: '', windowSeconds: '', minimumSamples: '' })
  const [validationError, setValidationError] = useState(null)
  const thresholdRule = draft.type !== 'TRACE_ERROR'
  const change = (key, value) => setDraft((current) => ({ ...current, [key]: value }))
  async function submit(event) {
    event.preventDefault()
    setValidationError(null)
    try {
      const request = buildRuleRequest(draft)
      if (await onCreate(request)) setDraft((current) => ({ ...current, name: '' }))
    } catch (failure) { setValidationError(failure.message) }
  }
  return <details className="alert-create"><summary>Create alert rule</summary><form onSubmit={submit} aria-label="Create alert rule" aria-busy={busy?.kind === 'create'}>
    <fieldset disabled={Boolean(busy)}><legend>Deterministic rule configuration</legend><div className="alert-form-grid">
      <label>Name<input required maxLength={255} value={draft.name} onChange={(e) => change('name', e.target.value)} /></label>
      <label>Type<select value={draft.type} onChange={(e) => change('type', e.target.value)}><option value="TRACE_ERROR">TRACE_ERROR</option><option value="ERROR_RATE_THRESHOLD">ERROR_RATE_THRESHOLD</option><option value="P95_LATENCY_THRESHOLD">P95_LATENCY_THRESHOLD</option></select></label>
      <label>Configured severity<select value={draft.severity} onChange={(e) => change('severity', e.target.value)}><option>INFO</option><option>WARNING</option><option>CRITICAL</option></select></label>
      <label>Exact service scope<input maxLength={255} value={draft.serviceName} onChange={(e) => change('serviceName', e.target.value)} aria-describedby="rule-scope-help" /></label>
      {thresholdRule ? <><label>Threshold {draft.type === 'ERROR_RATE_THRESHOLD' ? '(%)' : '(ms)'}<input required type="number" min="0" max={draft.type === 'ERROR_RATE_THRESHOLD' ? 100 : undefined} step="any" value={draft.threshold} onChange={(e) => change('threshold', e.target.value)} /></label><label>Window (seconds)<input required type="number" min="1" max="31536000" step="1" value={draft.windowSeconds} onChange={(e) => change('windowSeconds', e.target.value)} /></label><label>Minimum SERVER samples<input required type="number" min="1" max="1000000000" step="1" value={draft.minimumSamples} onChange={(e) => change('minimumSamples', e.target.value)} /></label></> : null}
      <label className="alert-checkbox"><input type="checkbox" checked={draft.enabled} onChange={(e) => change('enabled', e.target.checked)} />Enabled at creation</label>
    </div><p id="rule-scope-help" className="evidence-note">Blank scope means all services. Threshold comparator is fixed to ≥. TRACE_ERROR has no threshold/window configuration. Rules evaluate on subsequent telemetry commits.</p><button type="submit">{busy?.kind === 'create' ? 'Creating…' : 'Create rule'}</button></fieldset>
    {validationError || error ? <p role="alert" className="alert-action-error">{validationError || 'Rule creation failed. Verify the configuration and retry; Refresh checks persisted rules.'}</p> : null}
  </form></details>
}

function AlertRules({ rules, loading, error, busy, createError, onCreate }) {
  return <EvidencePanel title="Alert rules" note="Persisted rule configuration · configured severity is not incident severity">
    {loading ? <p role="status" aria-busy="true" className="evidence-state">Loading alert rules…</p> : null}
    {error ? <p role="alert" className="evidence-state">Alert rules unavailable. Use Refresh to retry.{rules ? ' Last returned configuration remains below.' : ''}</p> : null}
    {!loading && rules ? rules.length ? <div className="evidence-scroll" tabIndex={0} role="region" aria-label="Alert rules"><table className="evidence-table"><thead><tr><th scope="col">Rule / type</th><th scope="col">Enabled</th><th scope="col">Scope</th><th scope="col">Configured severity</th><th scope="col">Threshold / window / samples</th><th scope="col">Created / updated (UTC)</th></tr></thead><tbody>{rules.map((rule, index) => <tr key={rule.id ?? index}><th scope="row" className="evidence-name">{rule.name || 'Name unavailable'}<small>{rule.type || 'Type unavailable'} · ID {rule.id ?? '—'}</small></th><td>{rule.enabled === true ? 'Enabled' : rule.enabled === false ? 'Disabled' : '—'}</td><td className="evidence-name">{ruleScope(rule)}</td><td>{rule.severity || '—'}</td><td>{rule.type === 'TRACE_ERROR' ? 'Trace ERROR event · no threshold window' : <>{rule.comparator === 'GREATER_THAN_OR_EQUAL' ? '≥' : rule.comparator || '—'} {evidenceValue(rule.threshold, rule.type === 'ERROR_RATE_THRESHOLD' ? 'PERCENT' : rule.type === 'P95_LATENCY_THRESHOLD' ? 'MILLISECONDS' : null)}<small className="alert-meta">{formatCount(rule.windowSeconds)} s · minimum {formatCount(rule.minimumSamples)} samples</small></>}</td><td>{observationTime(rule.createdAt)}<small className="alert-meta">{observationTime(rule.updatedAt)}</small></td></tr>)}</tbody></table></div> : <p className="evidence-state" role="status">No alert rules configured.</p> : null}
    <RuleCreateForm busy={busy} error={createError} onCreate={onCreate} />
  </EvidencePanel>
}
export default AlertRules
