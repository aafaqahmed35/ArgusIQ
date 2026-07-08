import { useState } from 'react'
import AppShell from '../components/layout/AppShell'
import PageHeader from '../components/layout/PageHeader'
import MetricGrid from '../components/metrics/MetricGrid'
import LatencyOverviewPanel from '../components/analytics/LatencyOverviewPanel'
import TopEndpointsPanel from '../components/analytics/TopEndpointsPanel'
import ActivityFeed from '../components/activity/ActivityFeed'
import OperationsCenter from '../components/operations/OperationsCenter'
import InvestigationToolbar from '../components/traces/InvestigationToolbar'
import TraceDetailsDrawer from '../components/traces/TraceDetailsDrawer'
import TracePanel from '../components/traces/TracePanel'
import { useSystemHealth } from '../hooks/useSystemHealth'
import { useTraceAnalytics } from '../hooks/useTraceAnalytics'
import { useTraceFilters } from '../hooks/useTraceFilters'
import { useTraces } from '../hooks/useTraces'
import '../styles/dashboard.css'

function TraceDashboard() {
  const [selectedTrace, setSelectedTrace] = useState(null)
  const { traces, isLoading, error, websocketStatus, refreshTraces } = useTraces()
  const analytics = useTraceAnalytics(traces)
  const systemHealth = useSystemHealth({ traces, analytics, websocketStatus, isLoading, error })
  const { filteredTraces, filters, updateFilter, clearFilters, activeFilterCount, availableServices } =
    useTraceFilters(traces)
  const visibleSelectedTrace =
    selectedTrace && (activeFilterCount === 0 || filteredTraces.includes(selectedTrace)) ? selectedTrace : null

  return (
    <AppShell>
      <PageHeader websocketStatus={websocketStatus} isLoading={isLoading} onRefresh={refreshTraces} />
      <MetricGrid metrics={analytics.metrics} />
      <OperationsCenter health={systemHealth} />
      <section className="analytics-grid" aria-label="Trace analytics">
        <TopEndpointsPanel endpoints={analytics.topEndpoints} />
        <LatencyOverviewPanel
          averageResponseTime={analytics.averageResponseTime}
          p95ResponseTime={analytics.p95ResponseTime}
          slowestEndpoint={analytics.slowestEndpoint}
          formatDuration={analytics.formatDuration}
        />
      </section>
      <InvestigationToolbar
        filters={filters}
        updateFilter={updateFilter}
        clearFilters={clearFilters}
        activeFilterCount={activeFilterCount}
        availableServices={availableServices}
      />
      <section className="trace-workspace" aria-label="Trace workspace">
        <TracePanel
          traces={filteredTraces}
          isLoading={isLoading}
          error={error}
          onTraceSelect={setSelectedTrace}
          activeFilterCount={activeFilterCount}
          onClearFilters={clearFilters}
        />
        <ActivityFeed traces={traces} />
      </section>
      <TraceDetailsDrawer trace={visibleSelectedTrace} onClose={() => setSelectedTrace(null)} />
    </AppShell>
  )
}

export default TraceDashboard
