import assert from 'node:assert/strict'
import path from 'node:path'
import test, { before, after } from 'node:test'
import { fileURLToPath } from 'node:url'
import React from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter } from 'react-router-dom'
import { createServer } from 'vite'

let vite
const components = {}
before(async () => {
  vite = await createServer({ root: path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..'), appType: 'custom', logLevel: 'silent', server: { middlewareMode: true } })
  for (const [name, source] of Object.entries(sources)) components[name] = (await vite.ssrLoadModule(source)).default
})
after(async () => { await vite?.close() })
function render(name, props) {
  return renderToStaticMarkup(React.createElement(MemoryRouter, null, React.createElement(components[name], props)))
}

const sources = { list: '/src/components/services/ServiceListPanel.jsx', detail: '/src/components/services/ServiceDetailPanel.jsx' }
const service = {
  id: 7, serviceName: 'worker & dispatch', telemetryStatus: 'ERRORING', requestCount: 12, errorCount: 3, errorRate: 25,
  averageLatencyMs: 40, p95LatencyMs: 80, p99LatencyMs: 100, observedOperationCount: 1,
  topOperationsByTraffic: [{ serviceName: 'worker & dispatch', operationName: 'process batch', observationCount: 12, errorCount: 3 }],
  recentTraces: [{ traceId: 'test-trace', rootSpanName: 'process batch', statusCode: 'UNSET', startTime: '2026-10-01T01:00:00', durationMs: 40 }],
  recentErrors: [], language: 'java',
}
test('inventory uses genuine SERVER span metrics and native selection controls', () => {
  const html = render('list', { services: [service], selectedServiceId: 7, sortField: 'traffic' })
  assert.match(html, /SERVER spans, not unique traces/)
  assert.match(html, /aria-pressed="true"/)
  assert.match(html, /Inspect service worker &amp; dispatch/)
  assert.doesNotMatch(html, /<tr[^>]*role="button"/)
  assert.match(html, /25.0%/)
})
test('service links preserve exact entry-service filter semantics', () => {
  const html = render('list', { services: [service], sortField: 'traffic' })
  assert.match(html, /href="\/traces\?service=worker\+%26\+dispatch"/)
  assert.match(html, /not every participating service/)
})
test('selected service detail exposes operations and real runtime metadata', () => {
  const html = render('detail', { service })
  assert.match(html, /process batch/)
  assert.match(html, /href="\/traces\?query=process\+batch"/)
  assert.match(html, /Language<\/dt><dd>java/)
  assert.match(html, /Environment<\/dt><dd>Not observed/)
  assert.match(html, /all span kinds/)
})
test('recent traces use exact trace IDs and UTC timestamps', () => {
  const html = render('detail', { service })
  assert.match(html, /href="\/traces\?traceId=test-trace"/)
  assert.match(html, /2026-10-01 01:00:00 UTC/)
  assert.match(html, /Recent error evidence/)
  assert.match(html, /No matching traces in the returned evidence/)
})
test('relationships only show returned edges incident to selected service with structural caveat', () => {
  const html = render('detail', { service, relationships: { edges: [{ source: 'gateway', target: service.serviceName }, { source: 'unrelated-a', target: 'unrelated-b' }] } })
  assert.match(html, /Parent: <strong>gateway/)
  assert.doesNotMatch(html, /unrelated-a/)
  assert.match(html, /do not establish causation or synchronous calls/)
})
test('loading, error, empty inventory and no selection stay distinct', () => {
  assert.match(render('list', { services: [], isLoading: true }), /aria-busy="true"/)
  assert.match(render('list', { services: [], error: true }), /role="alert"/)
  assert.match(render('list', { services: [] }), /No services observed/)
  assert.match(render('detail', {}), /Select a service/)
  assert.match(render('detail', { isLoading: true }), /Loading persisted evidence/)
  assert.match(render('detail', { error: true }), /Backend evidence unavailable/)
})
test('missing detail fields never fabricate zeros or runtime identity', () => {
  const html = render('detail', { service: { serviceName: 'minimal' } })
  assert.match(html, /Server requests<\/dt><dd>—/)
  assert.match(html, /Operation evidence unavailable/)
  assert.match(html, /Relationship evidence unavailable/)
  assert.match(html, /Recent evidence unavailable/)
  assert.doesNotMatch(html, /No operation spans observed/)
  assert.match(render('detail', { service: { serviceName: 'empty operations', slowestOperation: null, fastestOperation: null, topOperationsByTraffic: [] } }), /No operation spans observed/)
  assert.doesNotMatch(html, /NaN|undefined|>0<\/dd>/)
})
test('relationship failures stay independent of available service evidence', () => {
  const html = render('detail', { service, relationshipError: true })
  assert.match(html, /role="alert"/)
  assert.match(html, /process batch/)
  assert.match(html, /Inspect trace/)
})
test('observation labels are not UP DOWN healthy or availability claims', () => {
  const html = render('list', { services: [service, { ...service, id: 8, telemetryStatus: 'ACTIVE' }, { ...service, id: 9, telemetryStatus: 'STALE' }] })
  assert.match(html, /Recently observed/)
  assert.match(html, /No recent telemetry/)
  assert.match(html, /Recent errors ≥10%/)
  assert.doesNotMatch(html, />UP<|>DOWN<|>Healthy<|uptime|99.9%|vs yesterday/i)
})
