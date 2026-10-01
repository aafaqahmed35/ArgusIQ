import assert from 'node:assert/strict'
import path from 'node:path'
import test, { after, before } from 'node:test'
import { fileURLToPath } from 'node:url'
import React from 'react'
import { MemoryRouter } from 'react-router-dom'
import { readFile } from 'node:fs/promises'
import { buildRuleRequest, createAlertInvestigationStore, investigationLinks, partitionOccurrences } from './alertInvestigation.js'
import { renderToStaticMarkup } from 'react-dom/server'
import { createServer } from 'vite'

const frontendRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..')
let vite
let Alerts
let TraceContext
let AlertsEvidence
let AlertDetailPanel
let RuleCreateForm

before(async () => {
  vite = await createServer({
    root: frontendRoot,
    appType: 'custom',
    logLevel: 'silent',
    server: { middlewareMode: true },
  })

  ;({ default: Alerts } = await vite.ssrLoadModule('/src/pages/Alerts.jsx'))
  ;({ default: AlertsEvidence } = await vite.ssrLoadModule('/src/components/alerts/AlertsEvidence.jsx'))
  ;({ default: AlertDetailPanel } = await vite.ssrLoadModule('/src/components/alerts/AlertDetailPanel.jsx'))
  ;({ RuleCreateForm } = await vite.ssrLoadModule('/src/components/alerts/AlertRules.jsx'))
  ;({ TraceContext } = await vite.ssrLoadModule('/src/context/traceContextCore.js'))
})

after(async () => {
  await vite?.close()
})

test('Alerts renders the shared WebSocket state without replacing it with local state', () => {
  for (const status of ['CONNECTING', 'LIVE', 'ERROR']) {
    const markup = renderToStaticMarkup(
      React.createElement(
        TraceContext.Provider,
        { value: { websocketStatus: status } },
        React.createElement(Alerts),
      ),
    )

    assert.match(markup, new RegExp(`connection-status--${status.toLowerCase()}`))
    assert.match(markup, new RegExp(`>${status}</span>`))
  }
})

// Deterministic contract fixtures: tests and isolated browser QA only.
const ruleFixture = { id: 11, name: 'Checkout error rate', type: 'ERROR_RATE_THRESHOLD', severity: 'WARNING', serviceName: 'checkout & orders', threshold: 5, windowSeconds: 300, minimumSamples: 20, comparator: 'GREATER_THAN_OR_EQUAL', enabled: true, createdAt: '2026-10-01T06:00:00', updatedAt: '2026-10-01T06:05:00' }
const occurrenceFixture = { alertId: 21, ruleId: 11, ruleName: 'Checkout error rate', severity: 'WARNING', status: 'OPEN', type: 'ERROR_RATE_THRESHOLD', description: 'Observed 8.4% error rate; configured threshold 5%.', acknowledged: false, firstTriggeredAt: '2026-10-01T06:00:00', lastTriggeredAt: '2026-10-01T06:05:00', evaluationTime: '2026-10-01T06:05:00', lastEvaluationState: 'MATCHED', relatedService: 'checkout & orders', evidence: { metric: 'ERROR_RATE_PERCENT', observedValue: 8.4, threshold: 5, unit: 'PERCENT', sampleCount: 500, errorCount: 42, windowStart: '2026-10-01T06:00:00', windowEnd: '2026-10-01T06:05:00', serviceName: 'checkout & orders' } }
const traceFixture = { ...occurrenceFixture, alertId: 22, ruleId: 12, ruleName: 'Direct trace error', type: 'TRACE_ERROR', description: 'Observed trace-level ERROR status.', relatedTrace: 'trace & / long-id', relatedSpan: 'span & / id', evidence: { metric: 'TRACE_STATUS_ERROR', observedValue: 1, unit: 'OBSERVED_EVENT', sampleCount: 1, errorCount: 1, status: 'ERROR', httpStatus: 503, operationName: 'POST /checkout', observedAt: '2026-10-01T06:05:00' } }
const acknowledgedFixture = { ...occurrenceFixture, alertId: 23, status: 'ACKNOWLEDGED', acknowledged: true, acknowledgedAt: '2026-10-01T06:06:00', lastEvaluationState: 'CLEAR', evaluationTime: '2026-10-01T06:07:00' }
const resolvedFixture = { ...traceFixture, alertId: 24, status: 'RESOLVED', resolvedAt: '2026-10-01T06:08:00' }
const readyState = { alerts: [occurrenceFixture, traceFixture, acknowledgedFixture, resolvedFixture], rules: [ruleFixture], occurrenceLoading: false, ruleLoading: false, occurrenceError: null, ruleError: null, busy: null }
// End deterministic fixtures.

function renderEvidence(patch = {}, props = {}) {
  return renderToStaticMarkup(React.createElement(MemoryRouter, null, React.createElement(AlertsEvidence, { state: { ...readyState, ...patch }, websocketStatus: 'ERROR', onSelect() {}, onRefresh() {}, onAction() {}, onCreateRule() {}, ...props })))
}
function renderDetail(alert, props = {}) {
  return renderToStaticMarkup(React.createElement(MemoryRouter, null, React.createElement(AlertDetailPanel, { alert, rule: ruleFixture, onClose() {}, onAction() {}, ...props })))
}
function apiFor() {
  return { fetchAlerts: async () => [occurrenceFixture], fetchAlertRules: async () => [ruleFixture], acknowledgeAlert: async (id) => ({ ...occurrenceFixture, alertId: id, status: 'ACKNOWLEDGED', acknowledged: true }), resolveAlert: async (id) => ({ ...occurrenceFixture, alertId: id, status: 'RESOLVED' }), createAlertRule: async (request) => ({ id: 99, ...request }) }
}
function deferred() { let resolve, reject; const promise = new Promise((yes, no) => { resolve = yes; reject = no }); return { promise, resolve, reject } }

test('loading states never display fabricated counts or empty states', () => {
  const html = renderEvidence({ alerts: null, rules: null, occurrenceLoading: true, ruleLoading: true })
  assert.match(html, /Loading alert occurrences/); assert.match(html, /Loading alert rules/)
  assert.match(html, /aria-busy="true"/); assert.match(html, /Active occurrences<\/dt><dd>—/)
  assert.doesNotMatch(html, /No active occurrences returned|No alert rules configured/)
})
test('summary derives active acknowledged open and resolved counts', () => {
  const html = renderEvidence()
  for (const [label, count] of [['Active occurrences',3],['Acknowledged · active',1],['Open · unacknowledged',2],['Resolved occurrences',1],['Enabled rules',1]]) assert.ok(html.includes(`${label}</dt><dd>${count}`))
})
test('no rules no active occurrences and no resolved history are distinct', () => {
  const html = renderEvidence({ alerts: [], rules: [] })
  for (const text of ['No alert rules configured','No active occurrences returned','No resolved occurrence history returned']) assert.ok(html.includes(text))
})
test('acknowledged CLEAR occurrence remains active with retained last-match evidence', () => {
  const groups = partitionOccurrences(readyState.alerts)
  assert.deepEqual(groups.active.map((a) => a.alertId), [21,22,23]); assert.deepEqual(groups.resolved.map((a) => a.alertId), [24])
  const html = renderEvidence({}, { selectedAlertId: 23 })
  assert.match(html, /Acknowledged · active/); assert.match(html, /CLEAR records a non-matching evaluation and does not itself resolve/)
  assert.match(html, /retained from the last match, not the CLEAR evaluation/)
})
test('resolved selection expands history and offers no lifecycle mutations', () => {
  assert.match(renderEvidence({}, {selectedAlertId:24}), /<details class="alert-history" open=""/)
  const html = renderDetail(resolvedFixture)
  assert.match(html, /Lifecycle<\/dt><dd>Resolved/); assert.doesNotMatch(html, /Acknowledge · keep active|Resolve · close occurrence/)
})
test('missing lifecycle is not silently classified active or resolved', () => {
  assert.match(renderEvidence({alerts:[{alertId:1}]}), /Occurrences with unavailable lifecycle/)
  const html = renderDetail({alertId:1}, {rule:undefined}); assert.match(html, /Occurrence state unavailable/); assert.doesNotMatch(html, /Resolve · close occurrence/)
})
test('backend failures never become no alerts or fabricated zeros', () => {
  const html = renderEvidence({alerts:null,rules:null,occurrenceError:true,ruleError:true})
  assert.match(html, /Alert occurrences unavailable/); assert.match(html, /Alert rules unavailable/); assert.match(html, /role="alert"/)
  assert.doesNotMatch(html, /No active occurrences returned|No alert rules configured|Active occurrences<\/dt><dd>0/)
})
test('independent failures preserve available evidence', () => {
  assert.match(renderEvidence({rules:null,ruleError:true}), /Inspect occurrence 21/)
  assert.match(renderEvidence({alerts:null,occurrenceError:true}), /Checkout error rate/)
})
test('refresh failure labels retained snapshots and suppresses current counts', () => {
  const html = renderEvidence({occurrenceError:true})
  assert.match(html, /Last returned occurrences remain below/); assert.match(html, /Active occurrences<\/dt><dd>—/); assert.match(html, /Inspect occurrence 21/)
})
test('disconnected shared live state cannot erase persisted REST evidence', () => {
  const html = renderEvidence({}, {selectedAlertId:21})
  assert.match(html, /connection-status--error/); assert.match(html, /persisted alert evidence remains independent/); assert.match(html, /Observed 8.4%/)
  assert.match(renderEvidence({}, {websocketStatus:'LIVE'}), /Shared trace live connection is available/)
})
test('rule configuration exposes genuine enabled disabled severity scope threshold and UTC fields', () => {
  const html = renderEvidence({rules:[ruleFixture,{...ruleFixture,id:12,enabled:false,serviceName:null,type:'TRACE_ERROR'}]})
  for (const text of ['ERROR_RATE_THRESHOLD','Configured severity','≥ 5%','300 s · minimum 20 samples','Disabled','All services','2026-10-01 06:00:00 UTC','Trace ERROR event · no threshold window']) assert.ok(html.includes(text),text)
})
test('detail exposes observed threshold samples errors scope and lifecycle timestamps', () => {
  const html = renderDetail(acknowledgedFixture)
  for (const text of ['8.4%','5%','500','42','ERROR_RATE_PERCENT','Configured scope','First triggered','Last triggered','Evaluated at','Acknowledged at','2026-10-01 06:06:00 UTC']) assert.ok(html.includes(text),text)
  assert.doesNotMatch(html, /Acknowledge · keep active/); assert.match(html, /Resolve · close occurrence/)
})
test('trace evidence exposes exact identifiers operation status and real HTTP status', () => {
  const html = renderDetail(traceFixture)
  for (const text of ['Trace ID','Span ID','trace &amp; / long-id','span &amp; / id','POST /checkout','Observed status','503']) assert.ok(html.includes(text),text)
})
test('missing fields never fabricate zeros identity acknowledgement or scope', () => {
  const html = renderDetail({alertId:1}, {rule:undefined})
  assert.match(html, /Observed value<\/dt><dd>—/); assert.match(html, /Acknowledged<\/dt><dd>—/); assert.match(html, /Configured scope<\/dt><dd>Scope unavailable/)
  assert.doesNotMatch(html, /NaN|undefined|>0<\/dd>/); assert.match(renderEvidence({rules:[{id:1}]}), /Scope unavailable/)
})
test('legacy evidence and timestamp limitations are explicit', () => {
  const html = renderDetail({...occurrenceFixture,lastEvaluationState:'LEGACY_IMPORTED',evidence:null})
  assert.match(html, /historical creation time/); assert.match(html, /structured match evidence may be unavailable/)
})
test('links preserve exact trace span and entry-service filters and honest broad operation search', () => {
  const links = investigationLinks(traceFixture)
  assert.deepEqual(links.map((l) => l.href), ['/traces?traceId=trace+%26+%2F+long-id','/traces?spanId=span+%26+%2F+id&traceId=trace+%26+%2F+long-id','/traces?service=checkout+%26+orders','/services','/traces?query=POST+%2Fcheckout'])
  const html = renderDetail(traceFixture)
  assert.match(html, /not an exact child-operation cohort/); assert.match(html, /Service inventory requires selection there/)
  assert.doesNotMatch(html, /httpStatus=|operation=|severity=/)
})
test('native selection and sibling lifecycle buttons avoid interactive row nesting', () => {
  const html = renderEvidence({}, {selectedAlertId:21})
  assert.doesNotMatch(html, /<tr[^>]*role="button"|<li[^>]*role="button"/)
  assert.match(html, /aria-label="Inspect occurrence 21"/); assert.match(html, /Acknowledge occurrence 21; keeps it active/); assert.match(html, /aria-pressed="true"/)
})
test('busy state disables lifecycle controls and announces the request', () => {
  const html = renderDetail(occurrenceFixture,{busy:{alertId:21,kind:'resolve'}})
  assert.match(html, /aria-busy="true"/); assert.match(html, /disabled=""/); assert.match(html, /Submitting resolve/)
  assert.match(renderEvidence({busy:{kind:'create'}}), /Creating…/)
})
test('create form offers only supported configuration', () => {
  const html = renderToStaticMarkup(React.createElement(RuleCreateForm,{onCreate(){}}))
  assert.match(html, /TRACE_ERROR/); assert.match(html, /P95_LATENCY_THRESHOLD/); assert.match(html, /Exact service scope/)
  assert.doesNotMatch(html, /Edit rule|Delete rule|Mute|Snooze|Slack|PagerDuty/)
})
test('trace rule serialization excludes thresholds and preserves disabled-at-creation', () => {
  assert.deepEqual(buildRuleRequest({name:' trace errors ',type:'TRACE_ERROR',severity:'INFO',serviceName:' ',enabled:false,threshold:'5',windowSeconds:'10',minimumSamples:'1'}),{name:'trace errors',type:'TRACE_ERROR',severity:'INFO',serviceName:null,enabled:false})
})
test('threshold serialization honors inclusive comparator and bounds without blank-to-zero coercion', () => {
  const draft = {name:'Errors',type:'ERROR_RATE_THRESHOLD',severity:'WARNING',serviceName:'checkout',enabled:true,threshold:'0',windowSeconds:'300',minimumSamples:'20'}
  assert.equal(buildRuleRequest(draft).comparator,'GREATER_THAN_OR_EQUAL'); assert.equal(buildRuleRequest(draft).threshold,0)
  for (const patch of [{threshold:''},{threshold:'101'},{windowSeconds:'0'},{minimumSamples:'1.5'},{windowSeconds:'31536001'},{name:' '}]) assert.throws(() => buildRuleRequest({...draft,...patch}))
})
test('acknowledge request uses exact ID and authoritative acknowledged response remains active', async () => {
  let rows=[occurrenceFixture],called; const api=apiFor(); api.fetchAlerts=async()=>rows
  api.acknowledgeAlert=async(id)=>{called=id;rows=[{...occurrenceFixture,status:'ACKNOWLEDGED',acknowledged:true}];return rows[0]}
  const store=createAlertInvestigationStore(api);await store.refresh();await store.mutate('acknowledge',21)
  assert.equal(called,21);assert.equal(partitionOccurrences(store.getSnapshot().alerts).active.length,1);assert.equal(store.getSnapshot().alerts[0].status,'ACKNOWLEDGED')
})
test('resolve request uses exact ID and authoritative resolved state enters history', async () => {
  let rows=[occurrenceFixture],called;const api=apiFor();api.fetchAlerts=async()=>rows
  api.resolveAlert=async(id)=>{called=id;rows=[{...occurrenceFixture,status:'RESOLVED'}];return rows[0]}
  const store=createAlertInvestigationStore(api);await store.refresh();await store.mutate('resolve',21)
  assert.equal(called,21);assert.equal(partitionOccurrences(store.getSnapshot().alerts).active.length,0);assert.equal(partitionOccurrences(store.getSnapshot().alerts).resolved.length,1)
})
test('refetched OPEN response wins over a requested resolve', async () => {
  const store=createAlertInvestigationStore(apiFor());await store.refresh();await store.mutate('resolve',21)
  assert.equal(store.getSnapshot().alerts[0].status,'OPEN')
})
test('mutation failure preserves original state and announces independent action failure', async () => {
  const api=apiFor();api.resolveAlert=async()=>{throw new Error('503')}
  const store=createAlertInvestigationStore(api);await store.refresh();await store.mutate('resolve',21)
  assert.equal(store.getSnapshot().alerts[0].status,'OPEN');assert.ok(store.getSnapshot().actionError);assert.equal(store.getSnapshot().occurrenceError,null)
  assert.match(renderEvidence({actionError:true}), /Lifecycle action failed/)
})
test('pending mutation prevents duplicates without an optimistic transition', async () => {
  const pending=deferred();let calls=0;const api=apiFor();api.resolveAlert=()=>{calls++;return pending.promise}
  const store=createAlertInvestigationStore(api);await store.refresh();const first=store.mutate('resolve',21);await store.mutate('resolve',21)
  assert.equal(calls,1);assert.equal(store.getSnapshot().alerts[0].status,'OPEN');assert.equal(store.getSnapshot().busy.kind,'resolve')
  pending.reject(new Error('failed'));await first;assert.equal(store.getSnapshot().busy,null)
})
test('stale pre-mutation GET cannot overwrite successful acknowledgement', async () => {
  const pending=deferred();let reads=0;const api=apiFor();api.fetchAlerts=()=>++reads===2?pending.promise:Promise.resolve(reads>2?[{...occurrenceFixture,status:'ACKNOWLEDGED',acknowledged:true}]:[occurrenceFixture])
  const store=createAlertInvestigationStore(api);await store.refresh();const refresh=store.refresh();await store.mutate('acknowledge',21)
  pending.resolve([occurrenceFixture]);await refresh;assert.equal(store.getSnapshot().alerts[0].status,'ACKNOWLEDGED')
})
test('failed post-mutation refetch retains successful response and marks snapshot unavailable', async () => {
  let reads=0;const api=apiFor();api.fetchAlerts=async()=>{if(++reads>1)throw new Error('503');return [occurrenceFixture]}
  const store=createAlertInvestigationStore(api);await store.refresh();await store.mutate('acknowledge',21)
  assert.equal(store.getSnapshot().alerts[0].status,'ACKNOWLEDGED');assert.ok(store.getSnapshot().occurrenceError);assert.equal(store.getSnapshot().actionError,null)
})
test('invalid independent REST response cannot become an empty authoritative array', async () => {
  const api=apiFor();api.fetchAlerts=async()=>null;const store=createAlertInvestigationStore(api);await store.refresh()
  assert.equal(store.getSnapshot().alerts,null);assert.ok(store.getSnapshot().occurrenceError);assert.equal(store.getSnapshot().rules.length,1)
})
test('create request reconciles authoritative returned rule configuration', async () => {
  const api=apiFor();let sent;api.createAlertRule=async(request)=>{sent=request;return {...request,id:99}};api.fetchAlertRules=async()=>sent?[{...sent,id:99}]:[]
  const store=createAlertInvestigationStore(api);await store.refresh();const request={name:'Trace error',type:'TRACE_ERROR',severity:'INFO',enabled:false,serviceName:null}
  assert.equal(await store.createRule(request),true);assert.deepEqual(sent,request);assert.equal(store.getSnapshot().rules[0].enabled,false);assert.match(store.getSnapshot().notice,/subsequent telemetry commits/)
})
test('create failure preserves rules and reports its own error', async () => {
  const api=apiFor();api.createAlertRule=async()=>{throw new Error('400')};const store=createAlertInvestigationStore(api);await store.refresh()
  assert.equal(await store.createRule({}),false);assert.equal(store.getSnapshot().rules.length,1);assert.ok(store.getSnapshot().createError)
  assert.match(renderEvidence({createError:true}),/Rule creation failed/)
})
test('shared connection changes cannot add a refetch dependency or duplicate socket owner', async () => {
  const source=await readFile(path.join(frontendRoot,'src/pages/Alerts.jsx'),'utf8')
  assert.match(source,/const \{ websocketStatus \} = useTraces\(\)/);assert.match(source,/\}, \[store\]\)/)
  assert.doesNotMatch(source,/connectTraceWebSocket|liveTraceSequence|\[websocketStatus\]/)
})
test('truthfulness excludes fake operational capabilities synthetic evidence and causal claims', () => {
  const html=renderEvidence({}, {selectedAlertId:22})
  assert.doesNotMatch(html,/PagerDuty|Slack notification|email notification|assignee|incident commander|MTTA|MTTR|root cause|AI insight|remediation|recommendation|healthy|uptime|availability guarantee|trend-arrow/i)
  assert.match(html,/Configured severity/);assert.match(html,/CLEAR does not automatically resolve/);assert.match(html,/not a causal explanation/)
  assert.doesNotMatch(html,/Resolved by|Acknowledged by|Repeat count<\/dt>/)
})
