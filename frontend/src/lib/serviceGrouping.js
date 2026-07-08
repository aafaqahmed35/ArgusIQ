import {
  calculateAverage,
  calculateP95,
  getPath,
  getDurationMs,
  summarizeEndpoints,
} from './traceAggregation'

export function getServiceGroupKey(rawPath) {
  if (!rawPath || rawPath === 'Unknown endpoint') {
    return '(unknown)'
  }

  let path = rawPath.split('?')[0].split('#')[0]

  if (rawPath.includes('://')) {
    try {
      path = new URL(rawPath).pathname
    } catch {
      // Keep the query-stripped path fallback.
    }
  }

  if (!path.startsWith('/')) {
    path = `/${path}`
  }

  const segments = path.split('/').filter(Boolean)

  if (segments.length === 0) {
    return '/'
  }

  if (segments[0] === 'api' && segments.length >= 2) {
    return `/${segments[0]}/${segments[1]}`
  }

  return `/${segments[0]}`
}

export function buildServiceGroups(traces) {
  const normalizedTraces = traces.map((trace) => {
    const path = getPath(trace)

    return {
      path,
      durationMs: getDurationMs(trace),
      serviceGroupKey: getServiceGroupKey(path),
    }
  })

  const totalRequests = normalizedTraces.length
  const groupMap = new Map()

  normalizedTraces.forEach((trace) => {
    const currentGroup = groupMap.get(trace.serviceGroupKey) ?? {
      key: trace.serviceGroupKey,
      name: trace.serviceGroupKey,
      traces: [],
    }

    currentGroup.traces.push(trace)
    groupMap.set(trace.serviceGroupKey, currentGroup)
  })

  const groups = [...groupMap.values()]
    .map((group) => {
      const durations = group.traces
        .map((trace) => trace.durationMs)
        .filter((duration) => duration !== null)
      const endpoints = summarizeEndpoints(group.traces)
      const requestCount = group.traces.length

      return {
        key: group.key,
        name: group.name,
        requestCount,
        averageResponseTime: calculateAverage(durations),
        p95ResponseTime: calculateP95(durations),
        trafficShare: totalRequests > 0 ? (requestCount / totalRequests) * 100 : 0,
        endpointCount: endpoints.length,
        endpoints,
      }
    })
    .sort((left, right) => right.requestCount - left.requestCount || left.name.localeCompare(right.name))

  return {
    groups,
    totalRequests,
    groupCount: groups.length,
  }
}
