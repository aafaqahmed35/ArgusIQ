import { useCallback, useEffect, useMemo, useState } from 'react'
import PageHeader from '../components/layout/PageHeader'
import AnalyticsEndpointDetail from '../components/analytics/AnalyticsEndpointDetail'
import AnalyticsSummaryStrip from '../components/analytics/AnalyticsSummaryStrip'
import TopEndpointsPanel from '../components/analytics/TopEndpointsPanel'
import { formatDuration, useTraceAnalytics } from '../hooks/useTraceAnalytics'
import { useTraces } from '../hooks/useTraces'
import { fetchMetrics } from '../services/traceApi'
import '../styles/dashboard.css'

const SORT_FIELD = {
  TRAFFIC: 'traffic',
  LATENCY: 'latency',
}

function Analytics() {
  const { recentTraces, recentTraceLimit, isLoading, error, refreshRecentTraces } = useTraces()
  const analytics = useTraceAnalytics(recentTraces)
  const [selectedEndpoint, setSelectedEndpoint] = useState(null)
  const [sortField, setSortField] = useState(SORT_FIELD.TRAFFIC)
  const [aggregateMetrics, setAggregateMetrics] = useState(null)
  const [isAggregateLoading, setIsAggregateLoading] = useState(true)
  const [aggregateError, setAggregateError] = useState(null)

  const loadAggregateMetrics = useCallback(async () => {
    setIsAggregateLoading(true)
    setAggregateError(null)

    try {
      setAggregateMetrics(await fetchMetrics())
    } catch (requestError) {
      setAggregateMetrics(null)
      setAggregateError(requestError)
    } finally {
      setIsAggregateLoading(false)
    }
  }, [])

  useEffect(() => {
    queueMicrotask(loadAggregateMetrics)
  }, [loadAggregateMetrics])

  const handleRefresh = useCallback(async () => {
    await Promise.all([refreshRecentTraces(), loadAggregateMetrics()])
  }, [loadAggregateMetrics, refreshRecentTraces])

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

  const recentTraceCount = recentTraces.length
  const aggregateDetail = aggregateError ? 'Backend aggregate unavailable' : 'All persisted traces · 30 s cache'

  const summaryItems = [
    {
      label: 'Total Traces',
      value: aggregateMetrics ? Number(aggregateMetrics.throughput).toLocaleString() : '—',
      detail: aggregateDetail,
    },
    {
      label: 'Average RT',
      value: formatDuration(aggregateMetrics?.averageLatencyMs),
      detail: aggregateDetail,
    },
    {
      label: 'P95',
      value: formatDuration(aggregateMetrics?.p95LatencyMs),
      detail: aggregateDetail,
    },
    {
      label: 'Endpoints',
      value: aggregateMetrics ? Number(aggregateMetrics.uniqueEndpoints).toLocaleString() : '—',
      detail: aggregateDetail,
    },
  ]

  const sourceNote = `Recent endpoint sample: latest ${recentTraceCount.toLocaleString()} traces (limit ${recentTraceLimit})`

  return (
    <div className="analytics-workspace">
      <section className="analytics-workspace__header" aria-label="Analytics header">
        <PageHeader
          title="Analytics"
          subtitle="Global summaries with a bounded recent endpoint drill-down."
          isLoading={isLoading || isAggregateLoading}
          onRefresh={handleRefresh}
          showConnectionStatus={false}
          statusNote="Global aggregate + recent trace window"
        />
      </section>

      <AnalyticsSummaryStrip items={summaryItems} isLoading={isAggregateLoading} />

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
          totalTraces={recentTraceCount}
          formatDuration={formatDuration}
        />
      </section>
    </div>
  )
}

export default Analytics
