/**
 * Service Statistics & Trace Metrics Utility for ArgusIQ.
 * Generates per-service breakdowns, latency distributions, and contribution rankings.
 */

export function calculateServiceStats(spans = [], totalTraceDurationMs = 1) {
  if (!Array.isArray(spans) || spans.length === 0) {
    return []
  }

  const serviceMap = new Map()

  spans.forEach((span) => {
    const serviceName = span.serviceName || 'Unknown Service'
    const duration = Number(span.durationMs) || 0
    const isError = (span.statusCode || '').toUpperCase() === 'ERROR'

    const stats = serviceMap.get(serviceName) || {
      serviceName,
      requestCount: 0,
      totalDuration: 0,
      maxDuration: 0,
      minDuration: Number.MAX_SAFE_INTEGER,
      errorCount: 0,
    }

    stats.requestCount += 1
    stats.totalDuration += duration
    if (duration > stats.maxDuration) stats.maxDuration = duration
    if (duration < stats.minDuration) stats.minDuration = duration
    if (isError) stats.errorCount += 1

    serviceMap.set(serviceName, stats)
  })

  const totalSumDuration = Array.from(serviceMap.values()).reduce((sum, s) => sum + s.totalDuration, 0) || 1

  const results = Array.from(serviceMap.values()).map((stats) => {
    const avgDuration = Math.round(stats.totalDuration / stats.requestCount)
    const contributionPct = Math.min(100, Math.round((stats.totalDuration / totalSumDuration) * 100))

    return {
      serviceName: stats.serviceName,
      requestCount: stats.requestCount,
      avgDurationMs: avgDuration,
      maxDurationMs: stats.maxDuration,
      minDurationMs: stats.minDuration === Number.MAX_SAFE_INTEGER ? 0 : stats.minDuration,
      errorCount: stats.errorCount,
      totalDurationMs: stats.totalDuration,
      contributionPct,
    }
  })

  // Sort by contribution percentage descending
  results.sort((a, b) => b.totalDurationMs - a.totalDurationMs)

  return results.map((item, index) => ({
    ...item,
    rank: index + 1,
    isSlowest: index === 0,
  }))
}
