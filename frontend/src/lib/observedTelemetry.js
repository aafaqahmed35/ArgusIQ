const TELEMETRY_STATUS_FIELDS = ['statusCode', 'status']
const HTTP_STATUS_FIELDS = ['httpStatus', 'httpStatusCode', 'exitStatus']

function firstValue(trace, fields) {
  const field = fields.find((name) => trace?.[name] !== undefined && trace?.[name] !== null && trace?.[name] !== '')
  return field ? trace[field] : null
}

export function classifyTraceObservation(trace) {
  const rawStatus = firstValue(trace, TELEMETRY_STATUS_FIELDS)
  const telemetryStatus = rawStatus === null ? null : String(rawStatus).trim().toUpperCase()
  const rawHttpStatus = firstValue(trace, HTTP_STATUS_FIELDS)
  const numericHttpStatus = Number(rawHttpStatus)
  const httpStatus = Number.isFinite(numericHttpStatus) ? numericHttpStatus : null
  const errorSpanCount = Number(trace?.errorSpanCount)
  const hasReportedErrorSpan = Number.isFinite(errorSpanCount) && errorSpanCount > 0

  if (telemetryStatus === 'ERROR' || hasReportedErrorSpan || (httpStatus !== null && httpStatus >= 500)) {
    return { kind: httpStatus !== null && httpStatus >= 500 ? 'SERVER_ERROR' : 'TRACE_ERROR', telemetryStatus, httpStatus }
  }
  if (httpStatus !== null && httpStatus >= 400) {
    return { kind: 'CLIENT_ERROR', telemetryStatus, httpStatus }
  }
  if (telemetryStatus === 'OK') {
    return { kind: 'OK', telemetryStatus, httpStatus }
  }
  if (telemetryStatus === 'UNSET') {
    return { kind: 'UNSET', telemetryStatus, httpStatus }
  }
  return { kind: 'UNKNOWN', telemetryStatus, httpStatus }
}

export function observedTelemetryStatus({ isLoading, observationCount, errorCount, latencyObserved }) {
  if (isLoading) return 'Loading'
  if (observationCount === 0) return 'No telemetry'
  if (errorCount > 0) return 'Errors observed'
  if (latencyObserved) return 'Latency observed'
  return 'Observed without errors'
}
