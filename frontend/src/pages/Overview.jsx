import { useCallback, useEffect, useMemo, useState } from 'react'
import PageHeader from '../components/layout/PageHeader'
import MetricGrid from '../components/metrics/MetricGrid'
import ActivityFeed from '../components/activity/ActivityFeed'
import OverviewChart from '../components/overview/OverviewChart'
import OverviewRuntimeSummary from '../components/overview/OverviewRuntimeSummary'
import { useSystemHealth } from '../hooks/useSystemHealth'
import { useTraceAnalytics } from '../hooks/useTraceAnalytics'
import { useTraces } from '../hooks/useTraces'
import {
  fetchAverageResponseTime,
  fetchHealth,
  fetchTraceCount,
} from '../services/traceApi'
import '../styles/dashboard.css'

function formatDuration(value) {
  if (value === null || value === undefined) {
    return '—'
  }

  return `${Math.round(value).toLocaleString()} ms`
}

function getHealthMetricTone(tone) {
  if (tone === 'success') {
    return 'live'
  }

  if (tone === 'warning') {
    return 'latency'
  }

  if (tone === 'error') {
    return 'error'
  }

  return 'connecting'
}

function Overview() {
  const { traces, isLoading, error, websocketStatus, refreshTraces } = useTraces()
  const analytics = useTraceAnalytics(traces)
  const systemHealth = useSystemHealth({ traces, analytics, websocketStatus, isLoading, error })
  const [backendMetrics, setBackendMetrics] = useState({
    traceCount: null,
    averageResponseTimeMs: null,
    health: null,
  })
  const [isBackendMetricsLoading, setIsBackendMetricsLoading] = useState(true)

  const loadBackendMetrics = useCallback(async () => {
    setIsBackendMetricsLoading(true)

    const [countResult, averageResult, healthResult] = await Promise.allSettled([
      fetchTraceCount(),
      fetchAverageResponseTime(),
      fetchHealth(),
    ])

    setBackendMetrics({
      traceCount: countResult.status === 'fulfilled' ? countResult.value?.count ?? null : null,
      averageResponseTimeMs:
        averageResult.status === 'fulfilled' ? averageResult.value?.averageResponseTimeMs ?? null : null,
      health: healthResult.status === 'fulfilled' ? healthResult.value ?? null : null,
    })
    setIsBackendMetricsLoading(false)
  }, [])

  useEffect(() => {
    queueMicrotask(loadBackendMetrics)
  }, [loadBackendMetrics])

  const handleRefresh = useCallback(async () => {
    await refreshTraces()
    await loadBackendMetrics()
  }, [loadBackendMetrics, refreshTraces])

  const overviewMetrics = useMemo(() => {
    const topEndpoint = analytics.topEndpoints[0]
    const traceCount = backendMetrics.traceCount ?? traces.length
    const averageResponseTime = backendMetrics.averageResponseTimeMs ?? analytics.averageResponseTime
    const traceCountDetail =
      backendMetrics.traceCount !== null ? 'From analytics API' : 'From loaded records'
    const averageDetail =
      backendMetrics.averageResponseTimeMs !== null ? 'From analytics API' : 'Across loaded traces'

    return [
      {
        label: 'Total Traces',
        value: Number(traceCount).toLocaleString(),
        detail: traceCountDetail,
        tone: 'signal',
      },
      {
        label: 'Average Response Time',
        value: formatDuration(averageResponseTime),
        detail: averageDetail,
        tone: 'latency',
      },
      {
        label: 'P95 Response Time',
        value: formatDuration(analytics.p95ResponseTime),
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
        label: 'Overall Health',
        value: systemHealth.status,
        detail: backendMetrics.health?.status
          ? `Backend reports ${backendMetrics.health.status}`
          : systemHealth.summary,
        tone: getHealthMetricTone(systemHealth.tone),
      },
    ]
  }, [analytics, backendMetrics, systemHealth, traces.length])

  const isOverviewLoading = isLoading || isBackendMetricsLoading

  return (
    <div className="overview-workspace">
      <section className="overview-workspace__header" aria-label="Overview header">
        <PageHeader
          title="Overview"
          subtitle="Executive summary of system health and telemetry."
          websocketStatus={websocketStatus}
          isLoading={isLoading}
          onRefresh={handleRefresh}
        />
      </section>

      <section className="overview-workspace__kpi" aria-label="Executive KPIs">
        <MetricGrid className="metric-grid--overview" metrics={overviewMetrics} isLoading={isOverviewLoading} />
      </section>

      <section className="overview-workspace__insights" aria-label="Overview insights">
        <OverviewChart traces={traces} isLoading={isOverviewLoading} />
        <OverviewRuntimeSummary
          health={systemHealth}
          backendHealth={backendMetrics.health}
          isLoading={isOverviewLoading}
        />
      </section>

      <section className="overview-workspace__activity" aria-label="Recent activity">
        <ActivityFeed
          traces={traces}
          isLoading={isLoading}
          limit={5}
          actionHref="/traces"
          actionLabel="Open Trace Explorer →"
          className="activity-feed--overview"
        />
      </section>
    </div>
  )
}

export default Overview
