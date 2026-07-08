import { useCallback, useEffect, useState } from 'react'
import { fetchTraces } from '../services/traceApi'
import { useTraceWebSocket } from './useTraceWebSocket'

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

function dedupeTraces(traces) {
  const seenTraceKeys = new Set()

  return traces.filter((trace) => {
    const traceKey = getTraceKey(trace)

    if (seenTraceKeys.has(traceKey)) {
      return false
    }

    seenTraceKeys.add(traceKey)
    return true
  })
}

export function useTraces() {
  const [traces, setTraces] = useState([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState(null)

  const loadTraces = useCallback(async () => {
    setIsLoading(true)
    setError(null)

    try {
      const data = await fetchTraces()
      setTraces(Array.isArray(data) ? dedupeTraces(data) : [])
    } catch (requestError) {
      setError(requestError)
    } finally {
      setIsLoading(false)
    }
  }, [])

  const handleRealtimeTrace = useCallback((trace) => {
    setTraces((currentTraces) => dedupeTraces([trace, ...currentTraces]))
  }, [])

  const websocketStatus = useTraceWebSocket(handleRealtimeTrace)

  useEffect(() => {
    queueMicrotask(loadTraces)
  }, [loadTraces])

  return {
    traces,
    isLoading,
    error,
    websocketStatus,
    refreshTraces: loadTraces,
  }
}
