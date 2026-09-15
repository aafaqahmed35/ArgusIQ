const OFFSET_SUFFIX = /(?:Z|[+-]\d{2}:?\d{2})$/i
const UTC_LOCAL_DATE_TIME = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?$/

export function normalizeBackendUtcTimestamp(value) {
  if (typeof value !== 'string') {
    return value
  }

  const timestamp = value.trim()
  if (!timestamp || OFFSET_SUFFIX.test(timestamp) || !UTC_LOCAL_DATE_TIME.test(timestamp)) {
    return timestamp
  }

  return `${timestamp}Z`
}

export function parseBackendUtcTimestamp(value) {
  if (value === null || value === undefined || value === '') {
    return null
  }

  const date = value instanceof Date ? new Date(value.getTime()) : new Date(normalizeBackendUtcTimestamp(value))
  return Number.isNaN(date.getTime()) ? null : date
}

export function backendUtcEpochMillis(value) {
  return parseBackendUtcTimestamp(value)?.getTime() ?? null
}
