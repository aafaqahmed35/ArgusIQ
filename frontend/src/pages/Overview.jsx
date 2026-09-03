import { useCallback, useEffect, useMemo, useState } from 'react'
import PageHeader from '../components/layout/PageHeader'
import MetricGrid from '../components/metrics/MetricGrid'
import ActivityFeed from '../components/activity/ActivityFeed'
import OverviewChart from '../components/overview/OverviewChart'
import OverviewRuntimeSummary from '../components/overview/OverviewRuntimeSummary'
import { useSystemHealth } from '../hooks/useSystemHealth'
import { useTraceAnalytics } from '../hooks/useTraceAnalytics'
import { useTraces } from '../hooks/useTraces'
import { fetchHealth, fetchMetrics } from '../services/traceApi'
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
  const {
    recentTraces,
    recentTraceLimit,
    isLoading,
    error,
    websocketStatus,
    refreshRecentTraces,
  } = useTraces()
  const analytics = useTraceAnalytics(recentTraces)
  const systemHealth = useSystemHealth({ recentTraces, analytics, websocketStatus, isLoading, error })
  const [backendHealth, setBackendHealth] = useState(null)
  const [backendMetrics, setBackendMetrics] = useState(null)
  const [isBackendSummaryLoading, setIsBackendSummaryLoading] = useState(true)

  const loadBackendSummary = useCallback(async () => {
    setIsBackendSummaryLoading(true)
    const [healthResult, metricsResult] = await Promise.allSettled([fetchHealth(), fetchMetrics()])

    setBackendHealth(healthResult.status === 'fulfilled' ? healthResult.value ?? null : null)
    setBackendMetrics(metricsResult.status === 'fulfilled' ? metricsResult.value ?? null : null)
    setIsBackendSummaryLoading(false)
  }, [])

  useEffect(() => {
    queueMicrotask(loadBackendSummary)
  }, [loadBackendSummary])

  const handleRefresh = useCallback(async () => {
    await Promise.all([refreshRecentTraces(), loadBackendSummary()])
  }, [loadBackendSummary, refreshRecentTraces])

  const overviewMetrics = useMemo(() => {
    const topEndpoint = analytics.topEndpoints[0]

    return [
      {
        label: 'Total Traces',
        value: backendMetrics ? Number(backendMetrics.totalTraces).toLocaleString() : '—',
        detail: 'All persisted traces · 30 s cache',
        tone: 'signal',
      },
      {
        label: 'Average Response Time',
        value: formatDuration(backendMetrics?.averageLatencyMs),
        detail: 'All persisted traces · 30 s cache',
        tone: 'latency',
      },
      {
        label: 'P95 Response Time',
        value: formatDuration(backendMetrics?.p95LatencyMs),
        detail: 'All persisted traces · 30 s cache',
        tone: 'latency',
      },
      {
        label: 'Top Recent Endpoint',
        value: topEndpoint?.endpoint ?? '—',
        detail: topEndpoint
          ? `${topEndpoint.count.toLocaleString()} of ${recentTraces.length.toLocaleString()} recent traces`
          : 'No recent requests yet',
        tone: 'source',
      },
      {
        label: 'Overall Health',
        value: systemHealth.status,
        detail: backendHealth?.status
          ? `Backend reports ${backendHealth.status}`
          : systemHealth.summary,
        tone: getHealthMetricTone(systemHealth.tone),
      },
    ]
  }, [analytics, backendHealth, backendMetrics, recentTraces.length, systemHealth])

  const isOverviewLoading = isLoading || isBackendSummaryLoading

  return (
    <div className="overview-workspace">
      <section className="overview-workspace__header" aria-label="Overview header">
        <PageHeader
          title="Overview"
          subtitle="Global backend summary with bounded recent operational signals."
          websocketStatus={websocketStatus}
          isLoading={isLoading}
          onRefresh={handleRefresh}
        />
      </section>

      <section className="overview-workspace__kpi" aria-label="Executive KPIs">
        <MetricGrid className="metric-grid--overview" metrics={overviewMetrics} isLoading={isOverviewLoading} />
      </section>

      <section className="overview-workspace__insights" aria-label="Overview insights">
        <OverviewChart
          recentTraces={recentTraces}
          recentTraceLimit={recentTraceLimit}
          isLoading={isOverviewLoading}
        />
        <OverviewRuntimeSummary
          health={systemHealth}
          backendHealth={backendHealth}
          isLoading={isOverviewLoading}
        />
      </section>

      <section className="overview-workspace__activity" aria-label="Recent activity">
        <ActivityFeed
          traces={recentTraces}
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
