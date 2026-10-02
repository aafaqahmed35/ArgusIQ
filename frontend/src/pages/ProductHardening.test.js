import assert from 'node:assert/strict'
import test, { before, after } from 'node:test'
import React from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter } from 'react-router-dom'
import { createServer } from 'vite'
let vite, modules
before(async () => {
  vite = await createServer({ appType: 'custom', logLevel: 'silent', server: { middlewareMode: true } })
  modules = await Promise.all(['components/traces/TracePanel', 'components/layout/PageHeader', 'components/overview/OverviewChart', 'hooks/useSystemHealth', 'components/layout/AppShell', 'components/overview/OverviewPanelState'].map((name) => vite.ssrLoadModule(`/src/${name}.${name.startsWith('hooks/') ? 'js' : 'jsx'}`)))
})
after(async () => { await vite?.close() })
function render(Component, props) { return renderToStaticMarkup(React.createElement(MemoryRouter, null, React.createElement(Component, props))) }
test('Trace Results compact state distinguishes failure from empty counts', () => {
  const html = render(modules[0].default, { traces: [], error: new Error('offline'), pagination: { totalItems: 0 } })
  assert.match(html, /trace-panel--state/)
  assert.match(html, /role="alert"/)
  assert.match(html, /— matches/)
  assert.doesNotMatch(html, /0 matches|No traces available/)
})
test('populated Trace Results keeps focusable contained table scrolling and native actions', () => {
  const html = render(modules[0].default, { traces: [{ traceId: 'trace' }], onTraceSelect() {} })
  assert.match(html, /tabindex="0" role="region" aria-label="Trace results table"/)
  assert.match(html, /<button[^>]*type="button"[^>]*class="trace-table__inspect"/)
  assert.doesNotMatch(html, /trace-panel--state/)
})
test('read-only headers do not offer an inert Refresh control', () => {
  assert.doesNotMatch(render(modules[1].default, { title: 'Read-only', showConnectionStatus: false }), /<button/)
  assert.match(render(modules[1].default, { onRefresh() {} }), />Refresh</)
})
test('Overview distribution failure does not report fabricated zero observations', () => {
  const html = render(modules[2].default, { metrics: null, state: 'error' })
  assert.match(html, /— traces/)
  assert.doesNotMatch(html, /0 traces/)
})
test('Overview recent-window failure differs from no telemetry independently of live state', () => {
  let snapshot
  function Probe() { snapshot = modules[3].useSystemHealth({ recentTraces: [], analytics: { averageResponseTime: null, p95ResponseTime: null }, websocketStatus: 'ERROR', isLoading: false, error: new Error('offline') }); return null }
  render(Probe)
  assert.equal(snapshot.status, 'Recent traces unavailable')
  assert.equal(snapshot.snapshot.find((item) => item.label === 'Recent Traces').value, '—')
  assert.ok(!snapshot.anomalies.includes('No recent traces'))
})
test('utility routes have current-page semantics and no fake identity', () => {
  const html = renderToStaticMarkup(React.createElement(MemoryRouter, { initialEntries: ['/settings'] }, React.createElement(modules[4].default)))
  assert.match(html, /aria-current="page"[^>]*href="\/settings"/)
  assert.match(html, /Skip to content/)
  assert.doesNotMatch(html, /Admin|>AI<|not yet available/)
})
test('shared loading state has an accessible status and errors remain alerts', () => {
  assert.match(render(modules[5].default, { state: 'loading' }), /role="status" aria-busy="true"/)
  assert.match(render(modules[5].default, { state: 'error' }), /role="alert"/)
})
