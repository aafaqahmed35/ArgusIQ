import { useMemo } from 'react'

const DATE_FIELDS = ['timestamp', 'createdAt', 'startTime', 'endTime']
const STATUS_FIELDS = ['status', 'statusCode', 'httpStatus']
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

function getStatusCode(trace) {
  const status = getFieldValue(trace, STATUS_FIELDS)
  const statusCode = Number(status)

  return Number.isFinite(statusCode) ? statusCode : null
}

function getTraceTime(trace) {
  const value = getFieldValue(trace, DATE_FIELDS)

  if (!value) {
    return null
  }

  const timestamp = new Date(value).getTime()
  return Number.isFinite(timestamp) ? timestamp : null
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
  }).format(new Date(value))
}

function getToneForStatus(status) {
  if (status === 'Healthy') {
    return 'success'
  }

  if (status === 'Degraded' || status === 'Loading') {
    return 'warning'
  }

  if (status === 'Critical' || status === 'Offline') {
    return 'error'
  }

  return 'neutral'
}

export function useSystemHealth({ recentTraces, analytics, websocketStatus, isLoading, error }) {
  return useMemo(() => {
    const recentTraceCount = recentTraces.length
    const durations = recentTraces.map(getDurationMs).filter((duration) => duration !== null)
    const statusCodes = recentTraces.map(getStatusCode).filter((statusCode) => statusCode !== null)
    const serverErrors = statusCodes.filter((statusCode) => statusCode >= 500).length
    const clientErrors = statusCodes.filter((statusCode) => statusCode >= 400 && statusCode < 500).length
    const errorCount = serverErrors + clientErrors
    const errorRate = statusCodes.length > 0 ? errorCount / statusCodes.length : 0
    const latestTraceTime = recentTraces.map(getTraceTime).filter(Boolean).sort((left, right) => right - left)[0] ?? null
    const latestTraceLabel = formatLatestTrace(latestTraceTime)
    const averageResponseTime = analytics.averageResponseTime
    const p95ResponseTime = analytics.p95ResponseTime
    const highLatencyObserved = durations.some((duration) => duration >= 1000)
    const elevatedResponseTimes =
      (averageResponseTime !== null && averageResponseTime >= 750) || (p95ResponseTime !== null && p95ResponseTime >= 1500)
    const backendUnavailable = Boolean(error)
    const websocketDisconnected = websocketStatus !== WEBSOCKET_LIVE && websocketStatus !== WEBSOCKET_CONNECTING
    const noRecentTraces = !isLoading && !error && recentTraceCount === 0

    const anomalies = [
      ...(backendUnavailable ? ['Backend unavailable'] : []),
      ...(websocketDisconnected ? ['WebSocket disconnected'] : []),
      ...(noRecentTraces ? ['No recent traces'] : []),
      ...(highLatencyObserved ? ['High latency observed'] : []),
      ...(elevatedResponseTimes ? ['Elevated response times'] : []),
      ...(serverErrors > 0 ? [`${serverErrors.toLocaleString()} server error traces observed`] : []),
    ]

    let status = 'Healthy'

    if (isLoading) {
      status = 'Loading'
    } else if (backendUnavailable) {
      status = 'Offline'
    } else if (serverErrors > 0 || errorRate >= 0.1 || elevatedResponseTimes || websocketDisconnected) {
      status = 'Critical'
    } else if (clientErrors > 0 || highLatencyObserved || noRecentTraces || websocketStatus === WEBSOCKET_CONNECTING) {
      status = 'Degraded'
    }

    const backendState = backendUnavailable ? 'Unavailable' : isLoading ? 'Loading' : 'Reachable'
    const restState = backendUnavailable ? 'Error' : isLoading ? 'Refreshing' : 'Connected'
    const websocketState =
      websocketStatus === WEBSOCKET_LIVE ? 'Live' : websocketStatus === WEBSOCKET_CONNECTING ? 'Connecting' : 'Disconnected'

    return {
      status,
      tone: getToneForStatus(status),
      summary:
        status === 'Healthy'
          ? 'All derived frontend signals are within normal bounds.'
          : 'Operations posture is based on the bounded recent trace window, REST state, and websocket status.',
      snapshot: [
        { label: 'Backend', value: backendState, tone: backendUnavailable ? 'error' : isLoading ? 'warning' : 'success' },
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
        { label: 'Error State', value: `${errorCount.toLocaleString()} issues`, detail: `${serverErrors.toLocaleString()} server / ${clientErrors.toLocaleString()} client`, tone: serverErrors > 0 ? 'error' : clientErrors > 0 ? 'warning' : 'success' },
      ],
      anomalies,
    }
  }, [analytics, error, isLoading, recentTraces, websocketStatus])
}
