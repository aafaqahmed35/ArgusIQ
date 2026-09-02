import axios from 'axios'

const API_BASE_URL = import.meta.env.VITE_ARGUSIQ_API_BASE_URL ?? 'http://localhost:8080/api/v1'

const traceClient = axios.create({
  baseURL: API_BASE_URL,
  withCredentials: true,
  withXSRFToken: true,
  headers: {
    Accept: 'application/json',
  },
})

export function buildTraceSearchParams(criteria = {}) {
  const params = {}

  Object.entries(criteria).forEach(([key, value]) => {
    if (value === undefined || value === null || value === '') {
      return
    }

    params[key] = value
  })

  return params
}

export async function searchTraces(criteria = {}, { signal } = {}) {
  const response = await traceClient.get('/search/traces', {
    params: buildTraceSearchParams(criteria),
    signal,
  })
  return response.data
}

export async function fetchTraceByTraceId(traceId) {
  if (!traceId) return null
  const response = await traceClient.get(`/traces/${encodeURIComponent(traceId)}`)
  return response.data
}

export async function fetchTraceCount() {
  const response = await traceClient.get('/traces/analytics/count')
  return response.data
}

export async function fetchAverageResponseTime() {
  const response = await traceClient.get('/traces/analytics/average-response-time')
  return response.data
}

export async function fetchMetrics() {
  const response = await traceClient.get('/metrics')
  return response.data
}

export async function fetchHealth() {
  const response = await traceClient.get('/health')
  return response.data
}
