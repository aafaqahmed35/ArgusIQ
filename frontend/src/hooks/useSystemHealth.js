import { useMemo } from 'react'
import { backendUtcEpochMillis, parseBackendUtcTimestamp } from '../lib/backendDateTime'
import { classifyTraceObservation, observedTelemetryStatus } from '../lib/observedTelemetry'

const DATE_FIELDS = ['timestamp', 'createdAt', 'startTime', 'endTime']
const DURATION_FIELDS = ['executionTimeMs', 'responseTime', 'duration', 'latency', 'responseTimeMs']

const WEBSOCKET_LIVE = 'LIVE'
const WEBSOCKET_CONNECTING = 'CONNECTING'

function getFieldValue(trace, fields, fallback = null) {
  const key = fields.find((field) => trace?.[field] !== undefined && trace?.[field] !== null && trace?.[field] !== '')
  return key ? trace[key] : fallback
}

function getDurationMs(trace) {
  const duration = getFieldValue(trace, DURATION_FIELDS)
  const durationMs = Number(duration)

  return Number.isFinite(durationMs) ? durationMs : null
}

function getTraceTime(trace) {
  const value = getFieldValue(trace, DATE_FIELDS)

  if (!value) {
    return null
  }

  return backendUtcEpochMillis(value)
}

function formatDuration(value) {
  if (value === null || value === undefined) {
    return 'No data'
  }

  return `${Math.round(value).toLocaleString()} ms`
}

function formatLatestTrace(value) {
  if (!value) {
    return 'No traces'
  }

  return new Intl.DateTimeFormat('en', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  }).format(parseBackendUtcTimestamp(value))
}

function getToneForStatus(status) {
  if (status === 'Latency observed' || status === 'Loading') {
    return 'warning'
  }

  if (status === 'Errors observed') {
    return 'error'
  }

  return 'neutral'
}

export function useSystemHealth({ recentTraces, analytics, websocketStatus, isLoading, error }) {
  return useMemo(() => {
    const recentTraceCount = recentTraces.length
    const durations = recentTraces.map(getDurationMs).filter((duration) => duration !== null)
    const observations = recentTraces.map(classifyTraceObservation)
    const serverErrors = observations.filter((observation) => observation.kind === 'SERVER_ERROR').length
    const clientErrors = observations.filter((observation) => observation.kind === 'CLIENT_ERROR').length
    const traceErrors = observations.filter((observation) => observation.kind === 'TRACE_ERROR').length
    const unsetCount = observations.filter((observation) => observation.kind === 'UNSET').length
    const errorCount = serverErrors + clientErrors + traceErrors
    const latestTraceTime = recentTraces.map(getTraceTime).filter(Boolean).sort((left, right) => right - left)[0] ?? null
    const latestTraceLabel = formatLatestTrace(latestTraceTime)
    const averageResponseTime = analytics.averageResponseTime
    const p95ResponseTime = analytics.p95ResponseTime
    const highLatencyObserved = durations.some((duration) => duration >= 1000)
    const elevatedResponseTimes =
      (averageResponseTime !== null && averageResponseTime >= 750) || (p95ResponseTime !== null && p95ResponseTime >= 1500)
    const backendUnavailable = Boolean(error)
    const websocketDisconnected = websocketStatus !== WEBSOCKET_LIVE && websocketStatus !== WEBSOCKET_CONNECTING
    const noRecentTraces = !isLoading && recentTraceCount === 0

    const anomalies = [
      ...(backendUnavailable ? ['Backend unavailable'] : []),
      ...(websocketDisconnected ? ['WebSocket disconnected'] : []),
      ...(noRecentTraces ? ['No recent traces'] : []),
      ...(highLatencyObserved ? ['High latency observed'] : []),
      ...(elevatedResponseTimes ? ['Elevated response times'] : []),
      ...(serverErrors > 0 ? [`${serverErrors.toLocaleString()} server error traces observed`] : []),
      ...(traceErrors > 0 ? [`${traceErrors.toLocaleString()} trace ERROR observations`] : []),
      ...(clientErrors > 0 ? [`${clientErrors.toLocaleString()} client-error observations`] : []),
      ...(unsetCount > 0 ? [`${unsetCount.toLocaleString()} traces reported UNSET status`] : []),
    ]

    const status = observedTelemetryStatus({
      isLoading,
      observationCount: recentTraceCount,
      errorCount,
      latencyObserved: highLatencyObserved || elevatedResponseTimes,
    })

    const backendState = backendUnavailable ? 'Unavailable' : isLoading ? 'Loading' : 'Reachable'
    const restState = backendUnavailable ? 'Error' : isLoading ? 'Refreshing' : 'Connected'
    const websocketState =
      websocketStatus === WEBSOCKET_LIVE ? 'Live' : websocketStatus === WEBSOCKET_CONNECTING ? 'Connecting' : 'Disconnected'

    return {
      status,
      tone: getToneForStatus(status),
      summary: 'Evidence is limited to the bounded recent trace window; it is not an availability or uptime claim.',
      snapshot: [
        { label: 'ArgusIQ API', value: backendState, tone: backendUnavailable ? 'error' : isLoading ? 'warning' : 'success' },
        { label: 'REST', value: restState, tone: backendUnavailable ? 'error' : isLoading ? 'warning' : 'success' },
        {
          label: 'WebSocket',
          value: websocketState,
          tone: websocketStatus === WEBSOCKET_LIVE ? 'success' : websocketStatus === WEBSOCKET_CONNECTING ? 'warning' : 'error',
        },
        { label: 'Latest Trace', value: latestTraceLabel, tone: latestTraceTime ? 'success' : 'neutral' },
        { label: 'Recent Traces', value: recentTraceCount.toLocaleString(), tone: recentTraceCount > 0 ? 'success' : 'neutral' },
      ],
      signals: [
        { label: 'REST', value: restState, detail: backendUnavailable ? 'Trace fetch failed' : 'Trace endpoint state', tone: backendUnavailable ? 'error' : isLoading ? 'warning' : 'success' },
        { label: 'Realtime', value: websocketState, detail: 'Websocket connection', tone: websocketStatus === WEBSOCKET_LIVE ? 'success' : websocketStatus === WEBSOCKET_CONNECTING ? 'warning' : 'error' },
        { label: 'Trace Intake', value: recentTraceCount > 0 ? 'Receiving' : 'No data', detail: `${recentTraceCount.toLocaleString()} recent records`, tone: recentTraceCount > 0 ? 'success' : 'neutral' },
        { label: 'Recent Latency', value: formatDuration(p95ResponseTime), detail: 'Recent-window P95', tone: elevatedResponseTimes ? 'error' : highLatencyObserved ? 'warning' : durations.length > 0 ? 'success' : 'neutral' },
        { label: 'Observed Errors', value: `${errorCount.toLocaleString()} traces`, detail: `${serverErrors.toLocaleString()} server HTTP / ${clientErrors.toLocaleString()} client HTTP / ${traceErrors.toLocaleString()} trace status`, tone: errorCount > 0 ? 'error' : 'neutral' },
      ],
      anomalies,
    }
  }, [analytics, error, isLoading, recentTraces, websocketStatus])
}
