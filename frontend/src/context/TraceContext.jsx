import { useCallback, useEffect, useState } from 'react'
import { searchTraces } from '../services/traceApi'
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

const RECENT_TRACE_LIMIT = 100

export function TraceProvider({ children }) {
  const [recentTraces, setRecentTraces] = useState([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState(null)
  const [websocketStatus, setConnectionStatus] = useState(WEBSOCKET_STATUS.CONNECTING)
  const [liveTraceSequence, setLiveTraceSequence] = useState(0)

  const loadRecentTraces = useCallback(async () => {
    setIsLoading(true)
    setError(null)

    try {
      const data = await searchTraces({
        page: 0,
        size: RECENT_TRACE_LIMIT,
        sortBy: 'startTime',
        sortDirection: 'desc',
      })
      setRecentTraces(Array.isArray(data?.items) ? dedupeTraces(data.items) : [])
    } catch (requestError) {
      setError(requestError)
    } finally {
      setIsLoading(false)
    }
  }, [])

  const handleRealtimeTrace = useCallback((trace) => {
    setRecentTraces((currentTraces) => dedupeTraces([trace, ...currentTraces]).slice(0, RECENT_TRACE_LIMIT))
    setLiveTraceSequence((currentSequence) => currentSequence + 1)
  }, [])

  useEffect(() => {
    let isSubscribed = true

    const initialLoadTimer = window.setTimeout(() => {
      loadRecentTraces()
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
  }, [loadRecentTraces, handleRealtimeTrace])

  return (
    <TraceContext.Provider
      value={{
        recentTraces,
        recentTraceLimit: RECENT_TRACE_LIMIT,
        isLoading,
        error,
        websocketStatus,
        liveTraceSequence,
        refreshRecentTraces: loadRecentTraces,
      }}
    >
      {children}
    </TraceContext.Provider>
  )
}
