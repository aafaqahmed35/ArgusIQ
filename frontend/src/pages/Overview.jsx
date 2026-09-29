import { useCallback, useEffect, useMemo, useState } from 'react'
import PageHeader from '../components/layout/PageHeader'
import MetricGrid from '../components/metrics/MetricGrid'
import ActivityFeed from '../components/activity/ActivityFeed'
import OverviewChart from '../components/overview/OverviewChart'
import OverviewInvestigationTargets from '../components/overview/OverviewInvestigationTargets'
import OverviewRuntimeSummary from '../components/overview/OverviewRuntimeSummary'
import OverviewTrafficProfile from '../components/overview/OverviewTrafficProfile'
import { useSystemHealth } from '../hooks/useSystemHealth'
import { useTraceAnalytics } from '../hooks/useTraceAnalytics'
import { useTraces } from '../hooks/useTraces'
import { fetchHealth, fetchMetrics } from '../services/traceApi'
import { buildOverviewMetrics, getPersistedMetricsState } from './overviewData'
import '../styles/dashboard.css'

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
  const [backendHealthError, setBackendHealthError] = useState(false)
  const [backendMetricsError, setBackendMetricsError] = useState(false)
  const [isBackendSummaryLoading, setIsBackendSummaryLoading] = useState(true)

  const loadBackendSummary = useCallback(async () => {
    setIsBackendSummaryLoading(true)
    const [healthResult, metricsResult] = await Promise.allSettled([fetchHealth(), fetchMetrics()])

    const healthFailed = healthResult.status === 'rejected'
    const metricsFailed = metricsResult.status === 'rejected'

    setBackendHealth(healthFailed ? null : healthResult.value ?? null)
    setBackendMetrics(metricsFailed ? null : metricsResult.value ?? null)
    setBackendHealthError(healthFailed)
    setBackendMetricsError(metricsFailed)
    setIsBackendSummaryLoading(false)
  }, [])

  useEffect(() => {
    queueMicrotask(loadBackendSummary)
  }, [loadBackendSummary])

  const handleRefresh = useCallback(async () => {
    await Promise.all([refreshRecentTraces(), loadBackendSummary()])
  }, [loadBackendSummary, refreshRecentTraces])

  const overviewMetrics = useMemo(() => buildOverviewMetrics(backendMetrics), [backendMetrics])
  const persistedMetricsState = getPersistedMetricsState({
    isLoading: isBackendSummaryLoading,
    error: backendMetricsError,
    metrics: backendMetrics,
  })
  const isOverviewRefreshing = isLoading || isBackendSummaryLoading

  return (
    <div className="overview-workspace">
      <section className="overview-workspace__header" aria-label="Overview header">
        <PageHeader
          title="Overview"
          subtitle="Persisted telemetry health, latency, traffic, and the latest investigation signals."
          websocketStatus={websocketStatus}
          isLoading={isOverviewRefreshing}
          onRefresh={handleRefresh}
          statusNote={`${recentTraces.length.toLocaleString()} / ${recentTraceLimit.toLocaleString()} recent`}
        />
      </section>

      <section className="overview-workspace__kpi" aria-label="Executive KPIs">
        <MetricGrid className="metric-grid--overview" metrics={overviewMetrics} isLoading={isBackendSummaryLoading} />
      </section>

      <section className="overview-workspace__insights" aria-label="Overview insights">
        <OverviewChart
          metrics={backendMetrics}
          state={persistedMetricsState}
          onRetry={handleRefresh}
        />
        <OverviewRuntimeSummary
          health={systemHealth}
          backendHealth={backendHealth}
          backendHealthError={backendHealthError}
          metricsState={persistedMetricsState}
          isLoading={isOverviewRefreshing}
          onRetry={handleRefresh}
        />
      </section>

      <section className="overview-workspace__signals" aria-label="Operational signals">
        <OverviewTrafficProfile
          metrics={backendMetrics}
          state={persistedMetricsState}
          onRetry={handleRefresh}
        />
        <OverviewInvestigationTargets
          metrics={backendMetrics}
          state={persistedMetricsState}
          onRetry={handleRefresh}
        />
      </section>

      <section className="overview-workspace__activity" aria-label="Recent activity">
        <ActivityFeed
          traces={recentTraces}
          isLoading={isLoading}
          limit={6}
          actionHref="/traces"
          actionLabel="Open Trace Explorer →"
          className="activity-feed--overview"
          error={error}
          onRetry={handleRefresh}
        />
      </section>
    </div>
  )
}

export default Overview
