import { useCallback, useEffect, useRef, useState } from 'react'
import PageHeader from '../components/layout/PageHeader'
import AnalyticsEvidence from '../components/analytics/AnalyticsEvidence'
import { fetchMetrics } from '../services/traceApi'
import '../styles/dashboard.css'
import '../styles/evidence.css'

function Analytics() {
  const [metrics, setMetrics] = useState(null)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState(null)
  const requestId = useRef(0)
  const refresh = useCallback(async () => {
    const current = ++requestId.current
    setIsLoading(true)
    setError(null)
    try {
      const data = await fetchMetrics()
      if (current === requestId.current) setMetrics(data)
    } catch (failure) {
      if (current === requestId.current) { setMetrics(null); setError(failure) }
    } finally {
      if (current === requestId.current) setIsLoading(false)
    }
  }, [])
  useEffect(() => {
    queueMicrotask(refresh)
    return () => { requestId.current += 1 }
  }, [refresh])
  return <div className="analytics-workspace evidence-workspace"><section className="analytics-workspace__header"><PageHeader title="Analytics" subtitle="Distributions and investigation rankings from persisted telemetry." showConnectionStatus={false} statusNote="Global aggregates · cache up to 30 s" isLoading={isLoading} onRefresh={refresh} /></section><AnalyticsEvidence metrics={metrics} isLoading={isLoading} error={error} /></div>
}

export default Analytics
