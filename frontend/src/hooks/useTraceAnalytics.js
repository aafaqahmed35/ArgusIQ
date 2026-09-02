import { useMemo } from 'react'
import {
  calculateAverage,
  calculateP95,
  formatDuration,
  normalizeTraces,
  summarizeEndpoints,
} from '../lib/traceAggregation'

export { formatDuration } from '../lib/traceAggregation'

export function useTraceAnalytics(recentTraces) {
  return useMemo(() => {
    const normalizedTraces = normalizeTraces(recentTraces)
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
          label: 'Recent Traces',
          value: recentTraces.length.toLocaleString(),
          detail: 'Bounded recent window',
          tone: 'signal',
        },
        {
          label: 'Average Response Time',
          value: formatDuration(averageResponseTime),
          detail: 'Across recent traces',
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
      endpoints,
      endpointCount: endpoints.length,
      formatDuration,
    }
  }, [recentTraces])
}
