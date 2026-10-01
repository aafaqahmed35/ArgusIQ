import { parseBackendUtcTimestamp } from '../lib/backendDateTime.js'

export function observationLabel(status) {
  return { ACTIVE: 'Recently observed', STALE: 'No recent telemetry', ERRORING: 'Recent errors ≥10%' }[status] || 'Observation status unavailable'
}

export function observationTime(value) {
  const date = parseBackendUtcTimestamp(value)
  return date ? `${date.toISOString().replace('T', ' ').replace('.000Z', '')} UTC` : '—'
}
