import { useCallback, useEffect, useState } from 'react'
import { fetchTraces } from '../services/traceApi'
import { connectTraceWebSocket } from '../websocket/websocketClient'
import { TraceContext, WEBSOCKET_STATUS, getTraceKey } from './traceContextCore'

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

    const initialLoadTimer = window.setTimeout(() => {
      loadTraces()
    }, 0)

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
      window.clearTimeout(initialLoadTimer)
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
