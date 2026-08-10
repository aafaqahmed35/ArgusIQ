import axios from 'axios'

const API_BASE_URL = 'http://localhost:8080/api/v1'

const traceClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    Accept: 'application/json',
  },
})

export async function fetchTraces() {
  const response = await traceClient.get('/traces')
  return response.data
}

export async function fetchSlowTraces() {
  const response = await traceClient.get('/traces/slow')
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

export async function fetchHealth() {
  const response = await traceClient.get('/health')
  return response.data
}
