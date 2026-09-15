import { useCallback, useEffect, useMemo, useState } from 'react'
import AlertTimeline from '../components/alerts/AlertTimeline'
import PageHeader from '../components/layout/PageHeader'
import { acknowledgeAlert, fetchAlerts, resolveAlert } from '../services/traceApi'
import '../styles/dashboard.css'

function Alerts() {
  const [alerts, setAlerts] = useState([])
  const [selectedAlertId, setSelectedAlertId] = useState(null)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState(null)

  const loadAlerts = useCallback(async () => {
    setIsLoading(true)
    setError(null)
    try {
      setAlerts(await fetchAlerts())
    } catch (requestError) {
      setError(requestError)
    } finally {
      setIsLoading(false)
    }
  }, [])

  useEffect(() => {
    queueMicrotask(loadAlerts)
  }, [loadAlerts])

  const selectedAlert = useMemo(
    () => alerts.find((alert) => alert.alertId === selectedAlertId) ?? null,
    [alerts, selectedAlertId],
  )

  const runAction = useCallback(async (action, alertId) => {
    setError(null)
    try {
      const updatedAlert = await action(alertId)
      setAlerts((current) => current.map((alert) => (alert.alertId === alertId ? updatedAlert : alert)))
    } catch (requestError) {
      setError(requestError)
    }
  }, [])

  return (
    <div className="alerts-workspace">
      <section className="alerts-workspace__header" aria-label="Alerts header">
        <PageHeader
          title="Alerts"
          subtitle="Deterministic rule matches backed by persisted telemetry evidence."
          isLoading={isLoading}
          onRefresh={loadAlerts}
          statusNote={`${alerts.length.toLocaleString()} recent occurrences`}
        />
      </section>

      <AlertTimeline
        alerts={alerts}
        error={error}
        isLoading={isLoading}
        onAcknowledge={(alertId) => runAction(acknowledgeAlert, alertId)}
        onAlertDeselect={() => setSelectedAlertId(null)}
        onAlertSelect={(alert) => setSelectedAlertId(alert.alertId)}
        onResolve={(alertId) => runAction(resolveAlert, alertId)}
        selectedAlert={selectedAlert}
      />
    </div>
  )
}

export default Alerts
