import { useCallback, useMemo, useState } from 'react'
import PageHeader from '../components/layout/PageHeader'
import AnalyticsEndpointDetail from '../components/analytics/AnalyticsEndpointDetail'
import AnalyticsSummaryStrip from '../components/analytics/AnalyticsSummaryStrip'
import TopEndpointsPanel from '../components/analytics/TopEndpointsPanel'
import { formatDuration, useTraceAnalytics } from '../hooks/useTraceAnalytics'
import { useTraces } from '../hooks/useTraces'
import '../styles/dashboard.css'

const SORT_FIELD = {
  TRAFFIC: 'traffic',
  LATENCY: 'latency',
}

function Analytics() {
  const { traces, isLoading, error, refreshTraces } = useTraces()
  const analytics = useTraceAnalytics(traces)
  const [selectedEndpoint, setSelectedEndpoint] = useState(null)
  const [sortField, setSortField] = useState(SORT_FIELD.TRAFFIC)

  const handleRefresh = useCallback(async () => {
    await refreshTraces()
  }, [refreshTraces])

  const sortedEndpoints = useMemo(() => {
    const endpoints = [...analytics.endpoints]

    if (sortField === SORT_FIELD.LATENCY) {
      return endpoints.sort((left, right) => {
        const leftLatency = left.averageResponseTime ?? -1
        const rightLatency = right.averageResponseTime ?? -1

        return rightLatency - leftLatency || right.count - left.count || left.endpoint.localeCompare(right.endpoint)
      })
    }

    return endpoints.sort(
      (left, right) => right.count - left.count || left.endpoint.localeCompare(right.endpoint),
    )
  }, [analytics.endpoints, sortField])

  const totalTraces = traces.length
  const averageResponseTime = analytics.averageResponseTime

  const summaryItems = [
    {
      label: 'Total Traces',
      value: Number(totalTraces).toLocaleString(),
      detail: 'From loaded traces',
    },
    {
      label: 'Average RT',
      value: formatDuration(averageResponseTime),
      detail: 'Computed from loaded traces',
    },
    {
      label: 'P95',
      value: formatDuration(analytics.p95ResponseTime),
      detail: 'Computed from loaded traces',
    },
    {
      label: 'Endpoints',
      value: analytics.endpointCount.toLocaleString(),
      detail: 'Distinct paths in loaded traces',
    },
  ]

  const sourceNote = `Based on ${Number(totalTraces).toLocaleString()} loaded traces`

  return (
    <div className="analytics-workspace">
      <section className="analytics-workspace__header" aria-label="Analytics header">
        <PageHeader
          title="Analytics"
          subtitle="Identify latency hotspots and traffic concentration across endpoints."
          isLoading={isLoading}
          onRefresh={handleRefresh}
          showConnectionStatus={false}
          statusNote="Snapshot from loaded traces"
        />
      </section>

      <AnalyticsSummaryStrip items={summaryItems} isLoading={isLoading} />

      <section className="analytics-workspace__body" aria-label="Endpoint analytics">
        <TopEndpointsPanel
          endpoints={sortedEndpoints}
          isLoading={isLoading}
          error={error}
          selectedEndpoint={selectedEndpoint?.endpoint ?? null}
          onEndpointSelect={setSelectedEndpoint}
          sortField={sortField}
          onSortFieldChange={setSortField}
          sourceNote={sourceNote}
          formatDuration={formatDuration}
        />
        <AnalyticsEndpointDetail
          endpoint={selectedEndpoint}
          totalTraces={totalTraces}
          formatDuration={formatDuration}
        />
      </section>
    </div>
  )
}

export default Analytics
