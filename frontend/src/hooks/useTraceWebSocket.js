import { useEffect, useState } from 'react'
import { connectTraceWebSocket } from '../services/traceWebSocket'

export const WEBSOCKET_STATUS = {
  CONNECTING: 'CONNECTING',
  LIVE: 'LIVE',
  ERROR: 'ERROR',
}

export function useTraceWebSocket(onTraceReceived) {
  const [connectionStatus, setConnectionStatus] = useState(WEBSOCKET_STATUS.CONNECTING)

  useEffect(() => {
    const disconnect = connectTraceWebSocket({
      onTraceReceived,
      onConnect: () => setConnectionStatus(WEBSOCKET_STATUS.LIVE),
      onError: () => setConnectionStatus(WEBSOCKET_STATUS.ERROR),
      onReconnecting: () => setConnectionStatus(WEBSOCKET_STATUS.CONNECTING),
    })

    return disconnect
  }, [onTraceReceived])

  return connectionStatus
}
