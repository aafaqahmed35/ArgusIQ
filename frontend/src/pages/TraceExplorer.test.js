import assert from 'node:assert/strict'
import test from 'node:test'
import { buildTraceSearchRequest, normalizeTraceSearchResult } from './traceExplorerSearch.js'

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
