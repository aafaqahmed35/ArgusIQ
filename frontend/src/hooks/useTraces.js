import { useContext } from 'react'
import { TraceContext } from '../context/traceContextCore'

export { getTraceKey } from '../context/traceContextCore'

export function useTraces() {
  const context = useContext(TraceContext)
  if (!context) {
    throw new Error('useTraces must be used within a TraceProvider')
  }
  return context
}
