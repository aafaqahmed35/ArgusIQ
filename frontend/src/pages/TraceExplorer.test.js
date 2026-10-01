import assert from 'node:assert/strict'
import path from 'node:path'
import test, { after, before } from 'node:test'
import { fileURLToPath } from 'node:url'
import React from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { createServer } from 'vite'
import { buildTraceSearchRequest, normalizeTraceSearchResult } from './traceExplorerSearch.js'

const frontendRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..')
let vite
let TraceTable
let CriticalPathBanner
let InvestigationFindings
let MiniTraceMap
let buildSpanTree

before(async () => {
  vite = await createServer({
    root: frontendRoot,
    appType: 'custom',
    logLevel: 'silent',
    server: { middlewareMode: true },
  })

  ;({ default: TraceTable } = await vite.ssrLoadModule('/src/components/TraceTable.jsx'))
  ;({ default: CriticalPathBanner } = await vite.ssrLoadModule('/src/components/traces/CriticalPathBanner.jsx'))
  ;({ default: InvestigationFindings } = await vite.ssrLoadModule('/src/components/traces/InvestigationFindings.jsx'))
  ;({ default: MiniTraceMap } = await vite.ssrLoadModule('/src/components/traces/MiniTraceMap.jsx'))
  ;({ buildSpanTree } = await vite.ssrLoadModule('/src/lib/spanTreeBuilder.js'))
})

after(async () => {
  await vite?.close()
})

const activeQuery = {
  query: 'checkout',
  traceId: '0123456789abcdef0123456789abcdef',
  spanId: '1111111111111111',
  service: 'checkout-service',
  endpoint: '/orders',
  httpMethod: 'GET',
  status: 'ERROR',
  latency: 'slow',
  from: '2026-09-23T10:00',
  to: '2026-09-23T11:00',
  page: 2,
  size: 50,
  sortBy: 'durationMs',
  sortDirection: 'asc',
}

test('a committed live trace invalidates the active server query without changing its filters', () => {
  const beforeNotification = buildTraceSearchRequest(activeQuery, 0, 10)
  const afterNotification = buildTraceSearchRequest(activeQuery, 0, 11)

  assert.notEqual(afterNotification.revision, beforeNotification.revision)
  assert.deepEqual(afterNotification.criteria, beforeNotification.criteria)
  assert.deepEqual(afterNotification.criteria, {
    query: 'checkout',
    traceId: '0123456789abcdef0123456789abcdef',
    spanId: '1111111111111111',
    serviceExact: 'checkout-service',
    endpoint: '/orders',
    httpMethod: 'GET',
    status: 'ERROR',
    from: '2026-09-23T10:00',
    to: '2026-09-23T11:00',
    page: 2,
    size: 50,
    sortBy: 'durationMs',
    sortDirection: 'asc',
    minDuration: 500,
    maxDuration: 999,
  })
})

test('live refresh replaces results with the authoritative server page instead of appending a socket payload', () => {
  const serverTrace = { traceId: 'server-result' }
  const socketTrace = { traceId: 'socket-payload' }
  const request = buildTraceSearchRequest(activeQuery, 0, 11)
  const result = normalizeTraceSearchResult(
    {
      items: [serverTrace],
      page: 2,
      size: 50,
      totalItems: 1,
      totalPages: 1,
      hasNext: false,
      hasPrevious: true,
    },
    request.criteria,
  )

  assert.deepEqual(result.items, [serverTrace])
  assert.equal(result.items.includes(socketTrace), false)
  assert.equal(result.items.length, 1)
})

test('manual refresh invalidates the same active query', () => {
  const beforeRefresh = buildTraceSearchRequest(activeQuery, 3, 10)
  const afterRefresh = buildTraceSearchRequest(activeQuery, 4, 10)

  assert.notEqual(afterRefresh.revision, beforeRefresh.revision)
  assert.deepEqual(afterRefresh.criteria, beforeRefresh.criteria)
})

test('connection-state changes cannot invalidate or loop the trace search', () => {
  const connecting = buildTraceSearchRequest(activeQuery, 3, 10)
  const live = buildTraceSearchRequest(activeQuery, 3, 10)
  const error = buildTraceSearchRequest(activeQuery, 3, 10)

  assert.deepEqual(live, connecting)
  assert.deepEqual(error, connecting)
})

test('trace results use native sibling actions instead of an interactive table row', () => {
  const markup = renderToStaticMarkup(React.createElement(TraceTable, {
    traces: [{
      traceId: 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
      serviceName: 'gateway',
      rootSpanName: 'GET /orders',
      httpMethod: 'GET',
      requestUri: '/orders',
      statusCode: 'OK',
      durationMs: 42,
      spanCount: 3,
      startTime: '2026-09-29T10:00:00',
    }],
    isLoading: false,
    error: null,
    onTraceSelect: () => {},
  }))

  assert.doesNotMatch(markup, /<tr[^>]+role="button"/)
  assert.doesNotMatch(markup, /<tr[^>]+tabindex=/)
  assert.match(markup, /aria-label="Copy trace ID a{32}"/)
  assert.match(markup, />Inspect<\/button>/)
  assert.match(markup, />3<\/td>/)
})

test('missing list fields remain explicitly unknown instead of becoming healthy or zero values', () => {
  const markup = renderToStaticMarkup(React.createElement(TraceTable, {
    traces: [{ traceId: 'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb' }],
    isLoading: false,
    error: null,
  }))

  assert.match(markup, /status-pill--neutral">—<\/span>/)
  assert.match(markup, />Unknown service<\/td>/)
  assert.match(markup, />Unknown operation<\/td>/)
  assert.doesNotMatch(markup, />AtlasBank<\/td>/)
})

test('span fallback keys are deterministic and missing service identity stays honest', () => {
  const first = buildSpanTree([{ name: 'unnamed source' }], {})
  const second = buildSpanTree([{ name: 'unnamed source' }], {})

  assert.deepEqual(Array.from(first.spanMap.keys()), ['span-0'])
  assert.deepEqual(Array.from(second.spanMap.keys()), ['span-0'])
  assert.equal(first.spanMap.get('span-0').serviceName, 'Unknown service')
})

test('topology renders only validated parent-child service edges', () => {
  const markup = renderToStaticMarkup(React.createElement(MiniTraceMap, {
    spans: [
      { spanId: 'root', serviceName: 'gateway' },
      { spanId: 'worker', parentSpanId: 'root', serviceName: 'orders' },
      { spanId: 'independent', serviceName: 'billing' },
    ],
  }))

  assert.match(markup, /gateway<\/code> <span[^>]*>→<\/span> <code>orders/)
  assert.doesNotMatch(markup, /billing<\/code> <span[^>]*>→/)
  assert.match(markup, /does not infer calls or causation/)
})

test('Critical Path and Explain preserve structural evidence limitations', () => {
  const criticalMarkup = renderToStaticMarkup(React.createElement(CriticalPathBanner, {
    criticalPathInfo: {
      status: 'COMPLETE',
      issues: [],
      algorithm: 'INTERVAL_AWARE_CAUSAL_V1',
      totalCriticalPathMs: 80,
      traceWallClockMs: 100,
      criticalPathPercentage: 80,
      largestContributor: null,
      criticalPathNodes: [],
    },
  }))
  const explainMarkup = renderToStaticMarkup(React.createElement(InvestigationFindings, {
    explanation: {
      status: 'PARTIAL',
      summary: 'Structural evidence is partial.',
      findings: [],
      limitations: [{ code: 'MISSING_PARENT', description: 'A parent span is missing.' }],
    },
  }))

  assert.match(criticalMarkup, /INTERVAL_AWARE_CAUSAL_V1/)
  assert.match(criticalMarkup, /do not prove synchronous waiting, causation, or root cause/)
  assert.match(explainMarkup, /Evidence strength describes support for the stated observation, not incident causation/)
  assert.match(explainMarkup, /Evidence Limitations/)
})
