const DURATION_FIELDS = ['executionTimeMs', 'responseTime', 'duration', 'latency', 'responseTimeMs']
const PATH_FIELDS = ['requestUri', 'path', 'endpoint', 'uri', 'url']

function getFirstFieldValue(trace, fields, fallback = null) {
  const field = fields.find(
    (fieldName) => trace?.[fieldName] !== undefined && trace?.[fieldName] !== null && trace?.[fieldName] !== '',
  )
  return field ? trace[field] : fallback
}

export function getDurationMs(trace) {
  const duration = getFirstFieldValue(trace, DURATION_FIELDS)

  if (duration === null) {
    return null
  }

  const durationMs = Number(duration)
  return Number.isFinite(durationMs) ? durationMs : null
}

export function getPath(trace) {
  return getFirstFieldValue(trace, PATH_FIELDS, 'Unknown endpoint') || 'Unknown endpoint'
}

export function formatDuration(value) {
  if (value === null || value === undefined) {
    return '—'
  }

  return `${Math.round(value).toLocaleString()} ms`
}

export function calculateAverage(values) {
  if (values.length === 0) {
    return null
  }

  return values.reduce((total, value) => total + value, 0) / values.length
}

export function calculateP95(values) {
  if (values.length === 0) {
    return null
  }

  const sortedValues = [...values].sort((left, right) => left - right)
  const index = Math.max(Math.ceil(sortedValues.length * 0.95) - 1, 0)
  return sortedValues[index]
}

export function normalizeTraces(traces) {
  return traces.map((trace) => ({
    path: getPath(trace),
    durationMs: getDurationMs(trace),
  }))
}

export function summarizeEndpoints(normalizedTraces) {
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
      endpoint: endpoint.endpoint,
      count: endpoint.count,
      averageResponseTime: calculateAverage(endpoint.durations),
      p95ResponseTime: calculateP95(endpoint.durations),
    }))
    .sort((left, right) => right.count - left.count || left.endpoint.localeCompare(right.endpoint))
}
