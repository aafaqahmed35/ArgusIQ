export const API_BASE_URL = import.meta.env.VITE_ARGUSIQ_API_BASE_URL ?? 'http://localhost:8080/api/v1'
export const WEBSOCKET_URL = import.meta.env.VITE_ARGUSIQ_WEBSOCKET_URL ?? 'http://localhost:8080/ws'

// Configuration is public client information. Never echo credentials or URL parameters.
export function endpointLabel(value) {
  try {
    const url = new URL(value, 'http://relative.invalid')
    if (!['http:', 'https:', 'ws:', 'wss:'].includes(url.protocol)) return 'Endpoint display unavailable'
    return `${url.origin === 'http://relative.invalid' ? '' : url.origin}${url.pathname}`
  } catch {
    return 'Endpoint display unavailable'
  }
}
