import { createContext, useCallback, useContext, useEffect, useState } from 'react'
import { fetchTraces } from '../services/traceApi'
import { connectTraceWebSocket } from '../websocket/websocketClient'

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

export const TraceContext = createContext(null)

export function TraceProvider({ children }) {
  const [traces, setTraces] = useState([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState(null)
  const [websocketStatus, setConnectionStatus] = useState(WEBSOCKET_STATUS.CONNECTING)

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

  useEffect(() => {
    let isSubscribed = true

    loadTraces()

    const disconnect = connectTraceWebSocket({
      onTraceReceived: (trace) => {
        if (isSubscribed) {
          handleRealtimeTrace(trace)
        }
      },
      onConnect: () => isSubscribed && setConnectionStatus(WEBSOCKET_STATUS.LIVE),
      onError: () => isSubscribed && setConnectionStatus(WEBSOCKET_STATUS.ERROR),
      onReconnecting: () => isSubscribed && setConnectionStatus(WEBSOCKET_STATUS.CONNECTING),
    })

    return () => {
      isSubscribed = false
      disconnect()
    }
  }, [loadTraces, handleRealtimeTrace])

  return (
    <TraceContext.Provider
      value={{
        traces,
        isLoading,
        error,
        websocketStatus,
        refreshTraces: loadTraces,
      }}
    >
      {children}
    </TraceContext.Provider>
  )
}

export function useTraceContext() {
  const context = useContext(TraceContext)
  if (!context) {
    throw new Error('useTraceContext must be used within a TraceProvider')
  }
  return context
}
