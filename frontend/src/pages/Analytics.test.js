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

const sources = { analytics: '/src/components/analytics/AnalyticsEvidence.jsx' }
const endpoint = { endpoint: '/orders & returns', requestCount: 10, errorCount: 2, errorRate: 20, averageLatencyMs: 50, p95LatencyMs: 90 }
const metrics = {
  totalTraces: 10, requestsPerMinute: 3, errorCount: 2, errorRate: 20, uniqueServices: 2, uniqueEndpoints: 1,
  p50LatencyMs: 30, p95LatencyMs: 90, p99LatencyMs: 99,
  traceDurationHistogram: { '251-500ms': 2, '0-100ms': 8, '101-250ms': 0 },
  httpMethodDistribution: { GET: 8, POST: 2 }, statusCodeDistribution: { ERROR: 2, UNSET: 8 },
  mostFailingEndpoints: [endpoint], slowestEndpoints: [endpoint], fastestEndpoints: [endpoint], topEndpointsByTraffic: [endpoint],
  slowestOperations: [{ operationName: 'worker process', serviceName: 'worker', observationCount: 2 }],
}
test('populated analytics renders real aggregates and explicit time scope', () => {
  const html = render('analytics', { metrics })
  assert.match(html, /Persisted traces/)
  assert.match(html, />10<\/dd>/)
  assert.match(html, /rolling UTC window/)
  assert.match(html, />99 ms<\/dd>/)
})
test('histogram is ordered distribution with actual counts and shares including zero buckets', () => {
  const html = render('analytics', { metrics })
  assert.ok(html.indexOf('0-100ms') < html.indexOf('101-250ms'))
  assert.ok(html.indexOf('101-250ms') < html.indexOf('251-500ms'))
  assert.match(html, /80.0%/)
  assert.match(html, /10 represented observations/)
  assert.match(html, /not a time series/)
})
test('composition distinguishes telemetry status from HTTP status and uses supported links', () => {
  const html = render('analytics', { metrics })
  assert.match(html, /href="\/traces\?httpMethod=GET"/)
  assert.match(html, /href="\/traces\?status=ERROR"/)
  assert.match(html, /not an HTTP response-code histogram/)
})
test('backend rankings preserve order and use encoded contains links without invented criteria', () => {
  const html = render('analytics', { metrics: { ...metrics, slowestEndpoints: [endpoint, { ...endpoint, endpoint: '/second' }] } })
  assert.match(html, /Most failing endpoints/)
  assert.match(html, /Fastest endpoints/)
  assert.match(html, /Busiest endpoints/)
  assert.match(html, /href="\/traces\?endpoint=%2Forders\+%26\+returns"/)
  assert.match(html, /href="\/traces\?query=worker\+process"/)
  assert.match(html, /not an exact operation cohort/)
  assert.doesNotMatch(html, /operation=|minDuration=/)
})
test('loading hides stale aggregates', () => {
  const html = render('analytics', { metrics, isLoading: true })
  assert.match(html, /aria-busy="true"/)
  assert.doesNotMatch(html, /Persisted traces/)
})
test('empty telemetry and backend error are distinct', () => {
  assert.match(render('analytics', { metrics: { totalTraces: 0 } }), /No telemetry yet/)
  const html = render('analytics', { error: true })
  assert.match(html, /role="alert"/)
  assert.doesNotMatch(html, /No telemetry yet|>0<\/dd>/)
})
test('missing and partial values remain unavailable rather than fabricated zeros', () => {
  const html = render('analytics', { metrics: { totalTraces: 1, httpMethodDistribution: { GET: null, POST: 2 } } })
  assert.match(html, /P99<\/dt><dd>—/)
  assert.match(html, /Distribution unavailable/)
  assert.match(html, /shares unavailable/)
  assert.match(html, /Ranking unavailable/)
  assert.doesNotMatch(html, /NaN|100.0%/)
})
test('analytics makes no synthetic health, availability, trend or RCA claims', () => {
  const html = render('analytics', { metrics })
  assert.doesNotMatch(html, /vs yesterday|vs last week|uptime|healthy|Apdex|AI insight|root cause|CPU|memory|trend-arrow/i)
})
