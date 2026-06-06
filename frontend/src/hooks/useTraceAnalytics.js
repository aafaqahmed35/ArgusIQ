import { useMemo } from 'react'

const DURATION_FIELDS = ['executionTimeMs', 'responseTime', 'duration', 'latency', 'responseTimeMs']
const PATH_FIELDS = ['requestUri', 'path', 'endpoint', 'uri', 'url']

function getFirstFieldValue(trace, fields, fallback = null) {
  const field = fields.find(
    (fieldName) => trace?.[fieldName] !== undefined && trace?.[fieldName] !== null && trace?.[fieldName] !== '',
  )
  return field ? trace[field] : fallback
}

function getDurationMs(trace) {
  const duration = getFirstFieldValue(trace, DURATION_FIELDS)

  if (duration === null) {
    return null
  }

  const durationMs = Number(duration)
  return Number.isFinite(durationMs) ? durationMs : null
}

function getPath(trace) {
  return getFirstFieldValue(trace, PATH_FIELDS, 'Unknown endpoint') || 'Unknown endpoint'
}

function formatDuration(value) {
  if (value === null || value === undefined) {
    return '—'
  }

  return `${Math.round(value).toLocaleString()} ms`
}

function calculateAverage(values) {
  if (values.length === 0) {
    return null
  }

  return values.reduce((total, value) => total + value, 0) / values.length
}

function calculateP95(values) {
  if (values.length === 0) {
    return null
  }

  const sortedValues = [...values].sort((left, right) => left - right)
  const index = Math.max(Math.ceil(sortedValues.length * 0.95) - 1, 0)
  return sortedValues[index]
}

function summarizeEndpoints(normalizedTraces) {
  const endpointMap = new Map()

  normalizedTraces.forEach((trace) => {
    const currentEndpoint = endpointMap.get(trace.path) ?? {
      endpoint: trace.path,
      count: 0,
      durations: [],
    }

    currentEndpoint.count += 1

    if (trace.durationMs !== null) {
      currentEndpoint.durations.push(trace.durationMs)
    }

    endpointMap.set(trace.path, currentEndpoint)
  })

  return [...endpointMap.values()]
    .map((endpoint) => ({
      ...endpoint,
      averageResponseTime: calculateAverage(endpoint.durations),
    }))
    .sort((left, right) => right.count - left.count || left.endpoint.localeCompare(right.endpoint))
}

export function useTraceAnalytics(traces) {
  return useMemo(() => {
    const normalizedTraces = traces.map((trace) => ({
      path: getPath(trace),
      durationMs: getDurationMs(trace),
    }))
    const durations = normalizedTraces.map((trace) => trace.durationMs).filter((duration) => duration !== null)
    const endpoints = summarizeEndpoints(normalizedTraces)
    const slowestEndpoint = endpoints
      .filter((endpoint) => endpoint.averageResponseTime !== null)
      .sort((left, right) => right.averageResponseTime - left.averageResponseTime)[0]
    const topEndpoint = endpoints[0]
    const averageResponseTime = calculateAverage(durations)
    const p95ResponseTime = calculateP95(durations)

    return {
      metrics: [
        {
          label: 'Total Traces',
          value: traces.length.toLocaleString(),
          detail: 'Loaded records',
          tone: 'signal',
        },
        {
          label: 'Average Response Time',
          value: formatDuration(averageResponseTime),
          detail: 'Across visible traces',
          tone: 'latency',
        },
        {
          label: 'P95 Response Time',
          value: formatDuration(p95ResponseTime),
          detail: '95th percentile latency',
          tone: 'latency',
        },
        {
          label: 'Top Endpoint',
          value: topEndpoint?.endpoint ?? '—',
          detail: topEndpoint ? `${topEndpoint.count.toLocaleString()} requests` : 'No requests yet',
          tone: 'source',
        },
        {
          label: 'Slowest Endpoint',
          value: slowestEndpoint?.endpoint ?? '—',
          detail: slowestEndpoint
            ? `${formatDuration(slowestEndpoint.averageResponseTime)} average`
            : 'No latency data',
          tone: 'error',
        },
      ],
      averageResponseTime,
      p95ResponseTime,
      slowestEndpoint,
      topEndpoints: endpoints.slice(0, 5),
      formatDuration,
    }
  }, [traces])
}
