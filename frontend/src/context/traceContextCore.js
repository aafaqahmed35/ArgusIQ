import { createContext } from 'react'

export const WEBSOCKET_STATUS = {
  CONNECTING: 'CONNECTING',
  LIVE: 'LIVE',
  ERROR: 'ERROR',
}

export function getTraceKey(trace) {
  if (trace?.id !== undefined && trace?.id !== null) {
    return `id:${trace.id}`
  }

  if (trace?.traceId !== undefined && trace?.traceId !== null) {
    return `traceId:${trace.traceId}`
  }

  return [
    trace?.httpMethod ?? trace?.method ?? '',
    trace?.requestUri ?? trace?.path ?? trace?.endpoint ?? '',
    trace?.timestamp ?? trace?.createdAt ?? '',
    trace?.executionTimeMs ?? trace?.responseTime ?? trace?.duration ?? '',
  ].join('|')
}

export const TraceContext = createContext(null)
