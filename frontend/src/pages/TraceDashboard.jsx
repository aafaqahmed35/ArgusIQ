import { useState } from 'react'
import AppShell from '../components/layout/AppShell'
import PageHeader from '../components/layout/PageHeader'
import MetricGrid from '../components/metrics/MetricGrid'
import LatencyOverviewPanel from '../components/analytics/LatencyOverviewPanel'
import TopEndpointsPanel from '../components/analytics/TopEndpointsPanel'
import ActivityFeed from '../components/activity/ActivityFeed'
import TraceDetailsDrawer from '../components/traces/TraceDetailsDrawer'
import TracePanel from '../components/traces/TracePanel'
import { useTraceAnalytics } from '../hooks/useTraceAnalytics'
import { useTraces } from '../hooks/useTraces'
import '../styles/dashboard.css'

function TraceDashboard() {
  const [selectedTrace, setSelectedTrace] = useState(null)
  const { traces, isLoading, error, websocketStatus, refreshTraces } = useTraces()
  const analytics = useTraceAnalytics(traces)

  return (
    <AppShell>
      <PageHeader websocketStatus={websocketStatus} isLoading={isLoading} onRefresh={refreshTraces} />
      <MetricGrid metrics={analytics.metrics} />
      <section className="analytics-grid" aria-label="Trace analytics">
        <TopEndpointsPanel endpoints={analytics.topEndpoints} />
        <LatencyOverviewPanel
          averageResponseTime={analytics.averageResponseTime}
          p95ResponseTime={analytics.p95ResponseTime}
          slowestEndpoint={analytics.slowestEndpoint}
          formatDuration={analytics.formatDuration}
        />
      </section>
      <section className="trace-workspace" aria-label="Trace workspace">
        <TracePanel traces={traces} isLoading={isLoading} error={error} onTraceSelect={setSelectedTrace} />
        <ActivityFeed traces={traces} />
      </section>
      <TraceDetailsDrawer trace={selectedTrace} onClose={() => setSelectedTrace(null)} />
    </AppShell>
  )
}

export default TraceDashboard
