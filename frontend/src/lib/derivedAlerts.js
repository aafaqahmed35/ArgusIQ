import { getTraceKey } from '../hooks/useTraces'
import { formatDuration, getDurationMs, getPath } from './traceAggregation'

export const ALERT_THRESHOLDS = {
  slowTraceMs: 1000,
  elevatedAverageMs: 750,
  elevatedP95Ms: 1500,
}

export const ALERT_SEVERITY = {
  CRITICAL: 'critical',
  WARNING: 'warning',
}

export const CONDITION_ALERT_IDS = {
  BACKEND_UNAVAILABLE: 'backend-unavailable',
  WEBSOCKET_DISCONNECTED: 'websocket-disconnected',
  ELEVATED_LATENCY: 'elevated-latency',
  NO_TRACES_LOADED: 'no-traces-loaded',
  SLOW_TRACES: 'slow-traces',
}

const WEBSOCKET_LIVE = 'LIVE'
const WEBSOCKET_CONNECTING = 'CONNECTING'
const MAX_EVENT_ALERTS = 5

const DATE_FIELDS = ['timestamp', 'createdAt', 'startTime', 'endTime']

const SEVERITY_ORDER = {
  [ALERT_SEVERITY.CRITICAL]: 0,
  [ALERT_SEVERITY.WARNING]: 1,
}

function getTraceTimestamp(trace) {
  const field = DATE_FIELDS.find(
    (fieldName) => trace?.[fieldName] !== undefined && trace?.[fieldName] !== null && trace?.[fieldName] !== '',
  )

  if (!field) {
    return null
  }

  const timestamp = new Date(trace[field]).getTime()
  return Number.isFinite(timestamp) ? timestamp : null
}

function formatAlertTimestamp(timestamp) {
  if (timestamp === null) {
    return 'Active now'
  }

  return new Intl.DateTimeFormat('en', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  }).format(new Date(timestamp))
}

function createAlert({
  id,
  severity,
  title,
  description,
  source,
  kind,
  detectedAt,
  evidence,
  investigation,
}) {
  return {
    id,
    severity,
    title,
    description,
    source,
    kind,
    detectedAt,
    evidence,
    investigation,
    timeLabel: formatAlertTimestamp(detectedAt),
  }
}

function sortAlerts(alerts) {
  return [...alerts].sort((left, right) => {
    const severityDifference = SEVERITY_ORDER[left.severity] - SEVERITY_ORDER[right.severity]

    if (severityDifference !== 0) {
      return severityDifference
    }

    const leftSortTime = left.detectedAt ?? Number.MAX_SAFE_INTEGER
    const rightSortTime = right.detectedAt ?? Number.MAX_SAFE_INTEGER

    if (leftSortTime !== rightSortTime) {
      return rightSortTime - leftSortTime
    }

    return left.id.localeCompare(right.id)
  })
}

export function buildDerivedAlerts({ traces, analytics, websocketStatus, isLoading, error }) {
  if (isLoading) {
    return []
  }

  const alerts = []
  const slowTraces = traces.filter((trace) => {
    const durationMs = getDurationMs(trace)
    return durationMs !== null && durationMs >= ALERT_THRESHOLDS.slowTraceMs
  })

  if (error) {
    alerts.push(
      createAlert({
        id: CONDITION_ALERT_IDS.BACKEND_UNAVAILABLE,
        severity: ALERT_SEVERITY.CRITICAL,
        title: 'Backend trace fetch failed',
        description: 'The frontend could not load traces from the REST API.',
        source: 'GET /api/v1/traces',
        kind: 'condition',
        detectedAt: null,
        evidence: null,
        investigation: { label: 'Investigate Pipeline →', href: '/infrastructure' },
      }),
    )
  }

  if (websocketStatus !== WEBSOCKET_LIVE && websocketStatus !== WEBSOCKET_CONNECTING) {
    alerts.push(
      createAlert({
        id: CONDITION_ALERT_IDS.WEBSOCKET_DISCONNECTED,
        severity: ALERT_SEVERITY.CRITICAL,
        title: 'WebSocket disconnected',
        description: 'The realtime trace stream is not connected.',
        source: 'WebSocket /ws',
        kind: 'condition',
        detectedAt: null,
        evidence: null,
        investigation: { label: 'Investigate Pipeline →', href: '/infrastructure' },
      }),
    )
  }

  if (!error && traces.length === 0) {
    alerts.push(
      createAlert({
        id: CONDITION_ALERT_IDS.NO_TRACES_LOADED,
        severity: ALERT_SEVERITY.WARNING,
        title: 'No traces loaded',
        description: 'The frontend has not received any trace records yet.',
        source: 'Loaded trace records',
        kind: 'condition',
        detectedAt: null,
        evidence: null,
        investigation: { label: 'Investigate Pipeline →', href: '/infrastructure' },
      }),
    )
  }

  const averageResponseTime = analytics.averageResponseTime
  const p95ResponseTime = analytics.p95ResponseTime
  const elevatedAverage =
    averageResponseTime !== null && averageResponseTime >= ALERT_THRESHOLDS.elevatedAverageMs
  const elevatedP95 = p95ResponseTime !== null && p95ResponseTime >= ALERT_THRESHOLDS.elevatedP95Ms

  if (!error && traces.length > 0 && (elevatedAverage || elevatedP95)) {
    alerts.push(
      createAlert({
        id: CONDITION_ALERT_IDS.ELEVATED_LATENCY,
        severity: ALERT_SEVERITY.WARNING,
        title: 'Elevated latency detected',
        description: `Average ${formatDuration(averageResponseTime)} · P95 ${formatDuration(p95ResponseTime)}`,
        source: 'Computed from loaded traces',
        kind: 'condition',
        detectedAt: null,
        evidence: {
          averageResponseTime,
          p95ResponseTime,
        },
        investigation: { label: 'Investigate Endpoints →', href: '/analytics' },
      }),
    )
  }

  if (!error && slowTraces.length > 0) {
    alerts.push(
      createAlert({
        id: CONDITION_ALERT_IDS.SLOW_TRACES,
        severity: ALERT_SEVERITY.WARNING,
        title: 'Slow traces detected',
        description: `${slowTraces.length.toLocaleString()} loaded traces exceed ${ALERT_THRESHOLDS.slowTraceMs.toLocaleString()} ms`,
        source: 'Computed from loaded traces',
        kind: 'condition',
        detectedAt: null,
        evidence: { slowTraceCount: slowTraces.length },
        investigation: { label: 'Investigate Endpoints →', href: '/analytics' },
      }),
    )
  }

  slowTraces
    .map((trace) => ({
      trace,
      detectedAt: getTraceTimestamp(trace),
      durationMs: getDurationMs(trace),
    }))
    .sort((left, right) => {
      if (left.detectedAt !== null && right.detectedAt !== null) {
        return right.detectedAt - left.detectedAt
      }

      if (left.detectedAt !== null) {
        return -1
      }

      if (right.detectedAt !== null) {
        return 1
      }

      return 0
    })
    .slice(0, MAX_EVENT_ALERTS)
    .forEach(({ trace, detectedAt, durationMs }) => {
      const path = getPath(trace)
      const traceIdentity = getTraceKey(trace)

      alerts.push(
        createAlert({
          id: `slow-trace-${traceIdentity}`,
          severity: ALERT_SEVERITY.WARNING,
          title: `Slow trace · ${path}`,
          description: `${formatDuration(durationMs)} response time on a loaded trace record.`,
          source: 'Trace timestamp',
          kind: 'event',
          detectedAt,
          evidence: {
            path,
            durationMs,
            traceIdentity,
          },
          investigation: { label: 'Investigate Requests →', href: '/traces' },
        }),
      )
    })

  return sortAlerts(alerts)
}
