import { useTraceContext } from '../context/TraceContext'

export { getTraceKey } from '../context/TraceContext'

export function useTraces() {
  return useTraceContext()
}
