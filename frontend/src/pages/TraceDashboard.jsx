import AppShell from '../components/layout/AppShell'
import PageHeader from '../components/layout/PageHeader'
import MetricGrid from '../components/metrics/MetricGrid'
import TracePanel from '../components/traces/TracePanel'
import { useTraces } from '../hooks/useTraces'
import '../styles/dashboard.css'

const RESPONSE_TIME_FIELDS = ['executionTimeMs', 'responseTime', 'duration', 'latency', 'responseTimeMs']

function getTraceResponseTime(trace) {
  const responseTime = RESPONSE_TIME_FIELDS.map((field) => trace?.[field])
    .filter((value) => value !== undefined && value !== null && value !== '')
    .map(Number)
    .find(Number.isFinite)

  return responseTime ?? null
}

function formatAverageResponseTime(traces) {
  const responseTimes = traces.map(getTraceResponseTime).filter((value) => value !== null)

  if (responseTimes.length === 0) {
    return '—'
  }

  const average = responseTimes.reduce((total, value) => total + value, 0) / responseTimes.length
  return `${Math.round(average).toLocaleString()} ms`
}

function TraceDashboard() {
  const { traces, isLoading, error, websocketStatus, refreshTraces } = useTraces()
  const metrics = [
    {
      label: 'Total Traces',
      value: traces.length.toLocaleString(),
      detail: 'Loaded records',
      tone: 'signal',
    },
    {
      label: 'Average Response Time',
      value: formatAverageResponseTime(traces),
      detail: 'Across visible traces',
      tone: 'latency',
    },
    {
      label: 'WebSocket Status',
      value: websocketStatus,
      detail: 'Live trace channel',
      tone: websocketStatus.toLowerCase(),
    },
    {
      label: 'Data Source',
      value: 'REST + WS',
      detail: 'Initial fetch plus stream',
      tone: 'source',
    },
  ]

  return (
    <AppShell>
      <PageHeader websocketStatus={websocketStatus} isLoading={isLoading} onRefresh={refreshTraces} />
      <MetricGrid metrics={metrics} />
      <TracePanel traces={traces} isLoading={isLoading} error={error} />
    </AppShell>
  )
}

export default TraceDashboard
