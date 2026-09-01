import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'

const WEBSOCKET_URL = import.meta.env.VITE_ARGUSIQ_WEBSOCKET_URL ?? 'http://localhost:8080/ws'
const TRACE_TOPIC = '/topic/traces'

export function createTraceWebSocketClient({ onTraceReceived, onConnect, onError, onReconnecting }) {
  const client = new Client({
    reconnectDelay: 5000,
    heartbeatIncoming: 4000,
    heartbeatOutgoing: 4000,
    webSocketFactory: () => new SockJS(WEBSOCKET_URL),
    onConnect: () => {
      onConnect?.()

      client.subscribe(TRACE_TOPIC, (message) => {
        try {
          const trace = JSON.parse(message.body)
          onTraceReceived?.(trace)
        } catch (parseError) {
          onError?.(parseError)
        }
      })
    },
    onStompError: (frame) => {
      onError?.(new Error(frame.headers.message || 'STOMP broker error'))
    },
    onWebSocketError: (event) => {
      onError?.(event)
    },
    onWebSocketClose: () => {
      if (client.active) {
        onReconnecting?.()
      }
    },
  })

  return client
}

export function connectTraceWebSocket(options) {
  const client = createTraceWebSocketClient(options)
  client.activate()

  return () => {
    client.deactivate()
  }
}
