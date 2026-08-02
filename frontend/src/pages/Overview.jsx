import { useCallback, useEffect, useMemo, useState } from 'react'
import PageHeader from '../components/layout/PageHeader'
import MetricGrid from '../components/metrics/MetricGrid'
import ActivityFeed from '../components/activity/ActivityFeed'
import OverviewChart from '../components/overview/OverviewChart'
import OverviewRuntimeSummary from '../components/overview/OverviewRuntimeSummary'
import { useSystemHealth } from '../hooks/useSystemHealth'
import { useTraceAnalytics } from '../hooks/useTraceAnalytics'
import { useTraces } from '../hooks/useTraces'
import { fetchHealth } from '../services/traceApi'
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
  const [backendHealth, setBackendHealth] = useState(null)
  const [isHealthLoading, setIsHealthLoading] = useState(true)

  const loadBackendHealth = useCallback(async () => {
    setIsHealthLoading(true)
    try {
      const data = await fetchHealth()
      setBackendHealth(data ?? null)
    } catch {
      setBackendHealth(null)
    } finally {
      setIsHealthLoading(false)
    }
  }, [])

  useEffect(() => {
    queueMicrotask(loadBackendHealth)
  }, [loadBackendHealth])

  const handleRefresh = useCallback(async () => {
    await refreshTraces()
    await loadBackendHealth()
  }, [loadBackendHealth, refreshTraces])

  const overviewMetrics = useMemo(() => {
    const topEndpoint = analytics.topEndpoints[0]

    return [
      {
        label: 'Total Traces',
        value: traces.length.toLocaleString(),
        detail: 'Loaded records',
        tone: 'signal',
      },
      {
        label: 'Average Response Time',
        value: formatDuration(analytics.averageResponseTime),
        detail: 'Across visible traces',
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
        detail: backendHealth?.status
          ? `Backend reports ${backendHealth.status}`
          : systemHealth.summary,
        tone: getHealthMetricTone(systemHealth.tone),
      },
    ]
  }, [analytics, backendHealth, systemHealth, traces.length])

  const isOverviewLoading = isLoading || isHealthLoading

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
          backendHealth={backendHealth}
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
