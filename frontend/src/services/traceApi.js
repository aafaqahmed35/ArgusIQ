import axios from 'axios'
import { API_BASE_URL } from '../config/runtimeConfig'

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

export async function fetchEndpointMetrics({ sortBy = 'traffic', sortDirection = 'desc', limit = 100 } = {}) {
  const response = await traceClient.get('/metrics/endpoints', {
    params: { sortBy, sortDirection, limit },
  })
  return response.data
}

export async function fetchServices() {
  const response = await traceClient.get('/services')
  return response.data
}

export async function fetchServiceRelationships() {
  const response = await traceClient.get('/services/dependencies')
  return response.data
}

export async function fetchService(serviceId) {
  const response = await traceClient.get(`/services/${encodeURIComponent(serviceId)}`)
  return response.data
}

export async function fetchHealth() {
  const response = await traceClient.get('/health')
  return response.data
}

export async function fetchAlerts() {
  const response = await traceClient.get('/alerts')
  return response.data
}

export async function acknowledgeAlert(alertId) {
  const response = await traceClient.post(`/alerts/${encodeURIComponent(alertId)}/acknowledge`)
  return response.data
}

export async function resolveAlert(alertId) {
  const response = await traceClient.post(`/alerts/${encodeURIComponent(alertId)}/resolve`)
  return response.data
}

export async function fetchAlertRules() {
  const response = await traceClient.get('/alerts/rules')
  return response.data
}

export async function createAlertRule(rule) {
  const response = await traceClient.post('/alerts/rules', rule)
  return response.data
}
