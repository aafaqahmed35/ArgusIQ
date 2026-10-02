import assert from 'node:assert/strict'
import test, { before, after } from 'node:test'
import React from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter } from 'react-router-dom'
import { createServer } from 'vite'

let vite, InfrastructureEvidence
before(async () => {
  vite = await createServer({ appType: 'custom', logLevel: 'silent', server: { middlewareMode: true } })
  ;({ InfrastructureEvidence } = await vite.ssrLoadModule('/src/pages/Infrastructure.jsx'))
})
after(async () => { await vite?.close() })
function render(props = {}) {
  return renderToStaticMarkup(React.createElement(MemoryRouter, null, React.createElement(InfrastructureEvidence, {
    isLoading: false, error: null, recentTraces: [], recentTraceLimit: 100, websocketStatus: 'ERROR', onRefresh() {}, ...props,
  })))
}
test('Infrastructure exposes application evidence and supported investigation routes', () => {
  const html = render()
  for (const label of ['Application traces', 'Service identities and runtime metadata', 'Request, error, and latency evidence', 'Structural service relationships']) assert.ok(html.includes(label))
  for (const route of ['/traces', '/services', '/analytics']) assert.ok(html.includes(`href="${route}"`))
  assert.match(html, /Validated parent/)
  assert.match(html, /Missing attributes remain unobserved/)
})
test('Infrastructure explicitly excludes host health and unsupported infrastructure evidence', () => {
  const html = render()
  for (const label of ['not ingested', 'Host CPU', 'host memory', 'disk', 'container metrics', 'Database', 'Redis/cache', 'Kafka/queue', 'uptime', 'availability']) assert.ok(html.includes(label))
  assert.match(html, /do not establish the condition/)
  assert.doesNotMatch(html, /Healthy|All systems operational|CPU utilization|under construction|Scheduled for/)
})
test('Infrastructure errors cannot become empty telemetry or zero counts', () => {
  const html = render({ error: new Error('offline') })
  assert.match(html, /role="alert"/)
  assert.match(html, /Backend evidence unavailable/)
  assert.doesNotMatch(html, /No recent traces returned|Recent trace records|>0<\/dd>/)
  assert.match(html, /What ArgusIQ observes/)
})
test('Infrastructure loading and empty states stay distinct', () => {
  assert.match(render({ isLoading: true }), /aria-busy="true"/)
  assert.doesNotMatch(render({ isLoading: true }), /No recent traces returned/)
  assert.match(render(), /No recent traces returned/)
})
test('a disconnected live connection does not hide available REST evidence or infer ingestion health', () => {
  const html = render({ recentTraces: [{ traceId: 'observed' }], websocketStatus: 'ERROR' })
  assert.match(html, /Live connection status: ERROR/)
  assert.match(html, /Response received/)
  assert.match(html, />1<\/dd>/)
  assert.match(html, /does not establish continuous ingestion/)
})
