import assert from 'node:assert/strict'
import path from 'node:path'
import test, { after, before } from 'node:test'
import { fileURLToPath } from 'node:url'
import React from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter } from 'react-router-dom'
import { createServer } from 'vite'
import {
  buildActivityItems,
  buildDistribution,
  buildInvestigationTargets,
  buildOverviewMetrics,
  getPersistedMetricsState,
  LATENCY_BUCKET_ORDER,
} from './overviewData.js'

const frontendRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..')
let vite
let MetricCard

before(async () => {
  vite = await createServer({
    root: frontendRoot,
    appType: 'custom',
    logLevel: 'silent',
    server: { middlewareMode: true },
  })

  ;({ default: MetricCard } = await vite.ssrLoadModule('/src/components/metrics/MetricCard.jsx'))
})

after(async () => {
  await vite?.close()
})

test('Overview maps persisted metrics without inventing deltas or substituting unavailable values', () => {
  const cards = buildOverviewMetrics({
    totalTraces: 1250,
    requestsPerMinute: 42,
    errorCount: 9,
    errorRate: 0.72,
    p95LatencyMs: 845.4,
    p99LatencyMs: null,
    uniqueServices: 7,
    uniqueEndpoints: 19,
  })

  assert.deepEqual(cards.map(({ label }) => label), [
    'Persisted Traces',
    'Requests / Min',
    'Error Rate',
    'P95 Latency',
    'P99 Latency',
    'Observed Services',
  ])
  assert.equal(cards[0].value, '1,250')
  assert.equal(cards[1].value, '42')
  assert.equal(cards[2].value, '0.7%')
  assert.equal(cards[3].value, '845 ms')
  assert.equal(cards[4].value, '—')
  assert.equal(cards[5].detail, '19 endpoints observed')
  assert.equal(cards.some((card) => 'trend' in card || 'delta' in card), false)
})

test('zero telemetry remains distinct from missing latency and rate values', () => {
  const cards = buildOverviewMetrics({
    totalTraces: 0,
    requestsPerMinute: 0,
    errorCount: 0,
    errorRate: null,
    p95LatencyMs: null,
    p99LatencyMs: null,
    uniqueServices: 0,
    uniqueEndpoints: 0,
  })

  assert.equal(cards[0].value, '0')
  assert.equal(cards[1].value, '0')
  assert.equal(cards[2].value, '—')
  assert.equal(cards[2].detail, 'No persisted telemetry')
  assert.equal(cards[3].value, '—')
  assert.equal(getPersistedMetricsState({ isLoading: false, error: false, metrics: { totalTraces: 0 } }), 'empty')
})

test('MetricCard preserves the exact spacing of textual values', () => {
  const markup = renderToStaticMarkup(
    React.createElement(
      MemoryRouter,
      null,
      React.createElement(MetricCard, {
        label: 'Observed Telemetry',
        value: 'No telemetry',
        detail: 'No persisted trace records',
      }),
    ),
  )

  assert.match(markup, />No telemetry<\/strong>/)
  assert.doesNotMatch(markup, /Notelemetry/)
})

test('persisted distributions preserve latency bucket order and report real shares', () => {
  const distribution = buildDistribution(
    {
      '2501ms+': 1,
      '251-500ms': 3,
      '0-100ms': 6,
    },
    LATENCY_BUCKET_ORDER,
  )

  assert.deepEqual(distribution.map(({ label }) => label), ['0-100ms', '251-500ms', '2501ms+'])
  assert.deepEqual(distribution.map(({ count }) => count), [6, 3, 1])
  assert.deepEqual(distribution.map(({ share }) => share), [60, 30, 10])
})

test('Overview investigation and activity links preserve supported Trace Explorer criteria', () => {
  const targets = buildInvestigationTargets({
    mostFailingEndpoints: [
      { endpoint: '/checkout', requestCount: 12, errorCount: 3, errorRate: 25 },
    ],
    slowestOperations: [
      { serviceName: 'gateway', operationName: 'POST /checkout', observationCount: 4, averageLatencyMs: 920 },
    ],
  })
  const activities = buildActivityItems([
    {
      traceId: '0123456789abcdef0123456789abcdef',
      startTime: '2026-09-27T10:00:00',
      serviceName: 'gateway',
      rootSpanName: 'POST /checkout',
      httpMethod: 'POST',
      requestUri: '/checkout',
      statusCode: 500,
      durationMs: 920,
    },
  ])

  assert.equal(targets.failingEndpoints[0].href, '/traces?endpoint=%2Fcheckout&status=ERROR')
  assert.equal(targets.slowOperations[0].href, '/traces?service=gateway&query=POST+%2Fcheckout')
  assert.equal(activities[0].href, '/traces?traceId=0123456789abcdef0123456789abcdef')
  assert.equal(activities[0].service, 'gateway')
  assert.equal(activities[0].statusTone, 'error')
})

test('WebSocket connection state cannot hide available persisted metrics', () => {
  const state = getPersistedMetricsState({
    isLoading: false,
    error: false,
    metrics: { totalTraces: 4 },
    websocketStatus: 'DISCONNECTED',
  })

  assert.equal(state, 'ready')
})
