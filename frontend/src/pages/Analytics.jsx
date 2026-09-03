import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import PageHeader from '../components/layout/PageHeader'
import AnalyticsEndpointDetail from '../components/analytics/AnalyticsEndpointDetail'
import AnalyticsSummaryStrip from '../components/analytics/AnalyticsSummaryStrip'
import TopEndpointsPanel from '../components/analytics/TopEndpointsPanel'
import { formatDuration } from '../lib/traceAggregation'
import { fetchEndpointMetrics, fetchMetrics } from '../services/traceApi'
import '../styles/dashboard.css'

const SORT_FIELD = {
  TRAFFIC: 'traffic',
  LATENCY: 'latency',
}

function Analytics() {
  const [selectedEndpoint, setSelectedEndpoint] = useState(null)
  const [sortField, setSortField] = useState(SORT_FIELD.TRAFFIC)
  const [aggregateMetrics, setAggregateMetrics] = useState(null)
  const [endpoints, setEndpoints] = useState([])
  const [isAggregateLoading, setIsAggregateLoading] = useState(true)
  const [isEndpointLoading, setIsEndpointLoading] = useState(true)
  const [aggregateError, setAggregateError] = useState(null)
  const [endpointError, setEndpointError] = useState(null)
  const aggregateRequestId = useRef(0)
  const endpointRequestId = useRef(0)

  const loadAggregateMetrics = useCallback(async () => {
    const requestId = ++aggregateRequestId.current
    setIsAggregateLoading(true)
    setAggregateError(null)

    try {
      const data = await fetchMetrics()
      if (requestId === aggregateRequestId.current) {
        setAggregateMetrics(data)
      }
    } catch (requestError) {
      if (requestId === aggregateRequestId.current) {
        setAggregateMetrics(null)
        setAggregateError(requestError)
      }
    } finally {
      if (requestId === aggregateRequestId.current) {
        setIsAggregateLoading(false)
      }
    }
  }, [])

  const loadEndpointMetrics = useCallback(async () => {
    const requestId = ++endpointRequestId.current
    setIsEndpointLoading(true)
    setEndpointError(null)

    try {
      const data = await fetchEndpointMetrics({ sortBy: sortField })
      if (requestId === endpointRequestId.current) {
        setEndpoints(Array.isArray(data) ? data : [])
      }
    } catch (requestError) {
      if (requestId === endpointRequestId.current) {
        setEndpoints([])
        setEndpointError(requestError)
      }
    } finally {
      if (requestId === endpointRequestId.current) {
        setIsEndpointLoading(false)
      }
    }
  }, [sortField])

  useEffect(() => {
    queueMicrotask(loadAggregateMetrics)
  }, [loadAggregateMetrics])

  useEffect(() => {
    queueMicrotask(loadEndpointMetrics)
  }, [loadEndpointMetrics])

  const handleRefresh = useCallback(async () => {
    await Promise.all([loadAggregateMetrics(), loadEndpointMetrics()])
  }, [loadAggregateMetrics, loadEndpointMetrics])

  const visibleSelectedEndpoint = useMemo(() => {
    if (!selectedEndpoint) return null
    return endpoints.find((endpoint) => endpoint.endpoint === selectedEndpoint.endpoint) ?? null
  }, [endpoints, selectedEndpoint])

  const aggregateDetail = aggregateError ? 'Backend aggregate unavailable' : 'All persisted traces · 30 s cache'
  const summaryItems = [
    {
      label: 'Total Traces',
      value: aggregateMetrics ? Number(aggregateMetrics.totalTraces).toLocaleString() : '—',
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

  return (
    <div className="analytics-workspace">
      <section className="analytics-workspace__header" aria-label="Analytics header">
        <PageHeader
          title="Analytics"
          subtitle="Persisted-history latency and traffic aggregates computed by PostgreSQL."
          isLoading={isAggregateLoading || isEndpointLoading}
          onRefresh={handleRefresh}
          showConnectionStatus={false}
          statusNote="Global persisted-history aggregates"
        />
      </section>

      <AnalyticsSummaryStrip items={summaryItems} isLoading={isAggregateLoading} />

      <section className="analytics-workspace__body" aria-label="Endpoint analytics">
        <TopEndpointsPanel
          endpoints={endpoints}
          isLoading={isEndpointLoading}
          error={endpointError}
          selectedEndpoint={visibleSelectedEndpoint?.endpoint ?? null}
          onEndpointSelect={setSelectedEndpoint}
          sortField={sortField}
          onSortFieldChange={setSortField}
          sourceNote="All persisted traces · maximum 100 ranked endpoints"
          formatDuration={formatDuration}
        />
        <AnalyticsEndpointDetail
          endpoint={visibleSelectedEndpoint}
          totalTraces={aggregateMetrics?.totalTraces ?? 0}
          formatDuration={formatDuration}
        />
      </section>
    </div>
  )
}

export default Analytics
