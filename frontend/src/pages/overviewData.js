import { backendUtcEpochMillis, parseBackendUtcTimestamp } from '../lib/backendDateTime.js'

export const LATENCY_BUCKET_ORDER = [
  '0-100ms',
  '101-250ms',
  '251-500ms',
  '501-1000ms',
  '1001-2500ms',
  '2501ms+',
]

const DATE_FIELDS = ['timestamp', 'createdAt', 'startTime', 'endTime']
const STATUS_FIELDS = ['status', 'statusCode', 'httpStatus']
const METHOD_FIELDS = ['method', 'httpMethod', 'requestMethod']
const PATH_FIELDS = ['requestUri', 'path', 'endpoint', 'uri', 'url']
const DURATION_FIELDS = ['durationMs', 'executionTimeMs', 'responseTime', 'duration', 'latency', 'responseTimeMs']
const SERVICE_FIELDS = ['serviceName', 'service']
const OPERATION_FIELDS = ['rootSpanName', 'operationName', 'operation']

function finiteNumber(value) {
  if (value === null || value === undefined || value === '') {
    return null
  }

  const number = Number(value)
  return Number.isFinite(number) ? number : null
}

function getFieldValue(source, fields, fallback = '—') {
  const key = fields.find(
    (field) => source?.[field] !== undefined && source?.[field] !== null && source?.[field] !== '',
  )
  return key ? source[key] : fallback
}

export function formatCount(value) {
  const number = finiteNumber(value)
  return number === null ? '—' : Math.round(number).toLocaleString()
}

export function formatDuration(value) {
  const number = finiteNumber(value)
  return number === null ? '—' : `${Math.round(number).toLocaleString()} ms`
}

export function formatPercent(value) {
  const number = finiteNumber(value)
  return number === null
    ? '—'
    : `${number.toLocaleString(undefined, { maximumFractionDigits: 1, minimumFractionDigits: 1 })}%`
}

export function buildOverviewMetrics(metrics) {
  const hasMetrics = metrics !== null && metrics !== undefined
  const totalTraces = finiteNumber(metrics?.totalTraces)
  const errorRate = finiteNumber(metrics?.errorRate)
  const errorCount = finiteNumber(metrics?.errorCount)
  const uniqueEndpoints = finiteNumber(metrics?.uniqueEndpoints)
  const uniqueServices = finiteNumber(metrics?.uniqueServices)
  const requestsPerMinute = finiteNumber(metrics?.requestsPerMinute)
  const p95Latency = finiteNumber(metrics?.p95LatencyMs)
  const p99Latency = finiteNumber(metrics?.p99LatencyMs)

  return [
    {
      label: 'Persisted Traces',
      value: formatCount(metrics?.totalTraces),
      detail: hasMetrics ? 'All stored trace records' : 'Persisted metrics unavailable',
      tone: 'source',
      href: '/traces',
    },
    {
      label: 'Requests / Min',
      value: formatCount(metrics?.requestsPerMinute),
      detail: requestsPerMinute === null ? 'Traffic rate unavailable' : 'Rolling one-minute UTC window',
      tone: requestsPerMinute > 0 ? 'live' : 'default',
      href: '/traces',
    },
    {
      label: 'Error Rate',
      value: formatPercent(metrics?.errorRate),
      detail:
        errorRate === null
          ? totalTraces === 0
            ? 'No persisted telemetry'
            : 'Error rate unavailable'
          : `${formatCount(errorCount)} persisted error ${errorCount === 1 ? 'trace' : 'traces'}`,
      tone: errorRate > 0 ? 'error' : errorRate === 0 ? 'live' : 'default',
      href: '/traces?status=ERROR',
    },
    {
      label: 'P95 Latency',
      value: formatDuration(metrics?.p95LatencyMs),
      detail:
        totalTraces === 0
          ? 'No latency observations'
          : p95Latency === null
            ? 'Latency percentile unavailable'
            : 'Persisted latency percentile',
      tone: 'latency',
      href: '/analytics',
    },
    {
      label: 'P99 Latency',
      value: formatDuration(metrics?.p99LatencyMs),
      detail:
        totalTraces === 0
          ? 'No latency observations'
          : p99Latency === null
            ? 'Latency percentile unavailable'
            : 'Persisted latency percentile',
      tone: 'latency',
      href: '/analytics',
    },
    {
      label: 'Observed Services',
      value: formatCount(metrics?.uniqueServices),
      detail:
        hasMetrics && uniqueServices !== null && uniqueEndpoints !== null
          ? `${formatCount(uniqueEndpoints)} ${uniqueEndpoints === 1 ? 'endpoint' : 'endpoints'} observed`
          : 'Service inventory unavailable',
      tone: 'signal',
      href: '/services',
    },
  ]
}

export function getPersistedMetricsState({ isLoading, error, metrics }) {
  if (isLoading) {
    return 'loading'
  }

  if (error || !metrics) {
    return 'error'
  }

  return finiteNumber(metrics.totalTraces) === 0 ? 'empty' : 'ready'
}

export function buildDistribution(source, preferredOrder = []) {
  const entries = Object.entries(source ?? {})
    .map(([label, value]) => ({ label, count: Math.max(0, finiteNumber(value) ?? 0) }))
    .filter(({ count }) => count > 0)

  const preferredIndex = new Map(preferredOrder.map((label, index) => [label, index]))
  entries.sort((left, right) => {
    const leftIndex = preferredIndex.get(left.label)
    const rightIndex = preferredIndex.get(right.label)

    if (leftIndex !== undefined || rightIndex !== undefined) {
      return (leftIndex ?? Number.MAX_SAFE_INTEGER) - (rightIndex ?? Number.MAX_SAFE_INTEGER)
    }

    return right.count - left.count || left.label.localeCompare(right.label)
  })

  const total = entries.reduce((sum, entry) => sum + entry.count, 0)
  return entries.map((entry) => ({
    ...entry,
    share: total > 0 ? (entry.count / total) * 100 : 0,
  }))
}

export function buildTraceSearchHref(criteria = {}) {
  const searchParams = new URLSearchParams()

  Object.entries(criteria).forEach(([key, value]) => {
    if (value !== null && value !== undefined && String(value).trim() !== '') {
      searchParams.set(key, String(value))
    }
  })

  const search = searchParams.toString()
  return search ? `/traces?${search}` : '/traces'
}

export function buildInvestigationTargets(metrics, limit = 3) {
  const failingEndpoints = (metrics?.mostFailingEndpoints ?? [])
    .filter((endpoint) => finiteNumber(endpoint?.errorCount) > 0)
    .slice(0, limit)
    .map((endpoint) => ({
      id: `endpoint-${endpoint.endpoint}`,
      primary: endpoint.endpoint || 'Unknown endpoint',
      secondary: `${formatCount(endpoint.errorCount)} errors · ${formatPercent(endpoint.errorRate)}`,
      value: `${formatCount(endpoint.requestCount)} requests`,
      href: buildTraceSearchHref({ endpoint: endpoint.endpoint, status: 'ERROR' }),
    }))

  const slowOperations = (metrics?.slowestOperations ?? [])
    .filter((operation) => finiteNumber(operation?.observationCount) > 0)
    .slice(0, limit)
    .map((operation) => ({
      id: `operation-${operation.serviceName}-${operation.operationName}`,
      primary: operation.operationName || 'Unknown operation',
      secondary: `${operation.serviceName || 'Unknown service'} · ${formatCount(operation.observationCount)} observations`,
      value: formatDuration(operation.averageLatencyMs),
      href: buildTraceSearchHref({ service: operation.serviceName, query: operation.operationName }),
    }))

  return { failingEndpoints, slowOperations }
}

function formatTimestamp(value) {
  const date = parseBackendUtcTimestamp(value)

  if (!date) {
    return value ? String(value) : '—'
  }

  return new Intl.DateTimeFormat('en', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  }).format(date)
}

function getActivityTone(status) {
  const statusText = String(status ?? '').trim().toUpperCase()
  const numericStatus = finiteNumber(status)

  if (statusText === 'ERROR' || (numericStatus !== null && numericStatus >= 500)) {
    return 'error'
  }

  if (statusText === 'UNSET' || statusText === 'WARN' || (numericStatus !== null && numericStatus >= 400)) {
    return 'warning'
  }

  if (statusText === 'OK' || numericStatus !== null) {
    return 'success'
  }

  return 'neutral'
}

export function buildActivityItems(traces, limit = 6) {
  return (traces ?? [])
    .map((trace, index) => {
      const timestampValue = getFieldValue(trace, DATE_FIELDS, null)
      const traceId = getFieldValue(trace, ['traceId', 'id'], null)
      const status = getFieldValue(trace, STATUS_FIELDS)

      return {
        id: traceId ?? `${getFieldValue(trace, PATH_FIELDS)}-${timestampValue ?? index}`,
        index,
        sortValue: backendUtcEpochMillis(timestampValue),
        timestamp: formatTimestamp(timestampValue),
        method: getFieldValue(trace, METHOD_FIELDS),
        path: getFieldValue(trace, PATH_FIELDS),
        duration: formatDuration(getFieldValue(trace, DURATION_FIELDS, null)),
        service: getFieldValue(trace, SERVICE_FIELDS, 'Unknown service'),
        operation: getFieldValue(trace, OPERATION_FIELDS, 'Unknown operation'),
        status,
        statusTone: getActivityTone(status),
        traceId,
        href: traceId ? buildTraceSearchHref({ traceId }) : '/traces',
      }
    })
    .sort((left, right) => {
      if (left.sortValue !== null && right.sortValue !== null) {
        return right.sortValue - left.sortValue
      }

      if (left.sortValue !== null) {
        return -1
      }

      if (right.sortValue !== null) {
        return 1
      }

      return left.index - right.index
    })
    .slice(0, limit)
}
