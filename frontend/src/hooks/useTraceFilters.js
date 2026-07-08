import { useCallback, useMemo, useState } from 'react'

const METHOD_FIELDS = ['method', 'httpMethod', 'requestMethod']
const PATH_FIELDS = ['requestUri', 'path', 'endpoint', 'uri', 'url']
const STATUS_FIELDS = ['status', 'statusCode', 'httpStatus']
const DURATION_FIELDS = ['executionTimeMs', 'responseTime', 'duration', 'latency', 'responseTimeMs']
const SERVICE_FIELDS = ['serviceName', 'service', 'applicationName', 'appName']

const DEFAULT_FILTERS = {
  search: '',
  method: 'all',
  statusCategory: 'all',
  latencyCategory: 'all',
  service: 'all',
}

function getFieldValue(trace, fields, fallback = '') {
  const key = fields.find((field) => trace?.[field] !== undefined && trace?.[field] !== null && trace?.[field] !== '')
  return key ? trace[key] : fallback
}

function getDurationMs(trace) {
  const duration = getFieldValue(trace, DURATION_FIELDS, null)
  const durationMs = Number(duration)

  return Number.isFinite(durationMs) ? durationMs : null
}

function getStatusCategory(trace) {
  const statusCode = Number(getFieldValue(trace, STATUS_FIELDS, null))

  if (!Number.isFinite(statusCode)) {
    return null
  }

  if (statusCode >= 200 && statusCode < 300) {
    return '2xx'
  }

  if (statusCode >= 400 && statusCode < 500) {
    return '4xx'
  }

  if (statusCode >= 500 && statusCode < 600) {
    return '5xx'
  }

  return null
}

function getLatencyCategory(trace) {
  const durationMs = getDurationMs(trace)

  if (durationMs === null) {
    return null
  }

  if (durationMs < 100) {
    return 'fast'
  }

  if (durationMs < 500) {
    return 'normal'
  }

  if (durationMs < 1000) {
    return 'slow'
  }

  return 'very-slow'
}

function normalize(value) {
  return String(value ?? '').trim().toLowerCase()
}

export function useTraceFilters(traces) {
  const [filters, setFilters] = useState(DEFAULT_FILTERS)

  const availableServices = useMemo(() => {
    const services = traces
      .map((trace) => String(getFieldValue(trace, SERVICE_FIELDS, '')).trim())
      .filter(Boolean)

    return [...new Set(services)].sort((left, right) => left.localeCompare(right))
  }, [traces])

  const filteredTraces = useMemo(() => {
    const searchQuery = normalize(filters.search)

    return traces.filter((trace) => {
      const path = normalize(getFieldValue(trace, PATH_FIELDS, ''))
      const method = normalize(getFieldValue(trace, METHOD_FIELDS, ''))
      const service = String(getFieldValue(trace, SERVICE_FIELDS, '')).trim()

      if (searchQuery && !path.includes(searchQuery)) {
        return false
      }

      if (filters.method !== 'all' && method !== normalize(filters.method)) {
        return false
      }

      if (filters.statusCategory !== 'all' && getStatusCategory(trace) !== filters.statusCategory) {
        return false
      }

      if (filters.latencyCategory !== 'all' && getLatencyCategory(trace) !== filters.latencyCategory) {
        return false
      }

      if (filters.service !== 'all' && service !== filters.service) {
        return false
      }

      return true
    })
  }, [filters, traces])

  const activeFilterCount = useMemo(
    () =>
      Object.entries(filters).filter(([key, value]) => {
        if (key === 'search') {
          return value.trim() !== ''
        }

        return value !== 'all'
      }).length,
    [filters],
  )

  const updateFilter = useCallback((filterName, value) => {
    setFilters((currentFilters) => ({
      ...currentFilters,
      [filterName]: value,
    }))
  }, [])

  const clearFilters = useCallback(() => {
    setFilters(DEFAULT_FILTERS)
  }, [])

  return {
    filteredTraces,
    filters,
    updateFilter,
    clearFilters,
    activeFilterCount,
    availableServices,
  }
}
