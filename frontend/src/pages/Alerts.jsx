import { useCallback, useMemo, useState } from 'react'
import AlertTimeline from '../components/alerts/AlertTimeline'
import DerivedAlertsBanner from '../components/alerts/DerivedAlertsBanner'
import PageHeader from '../components/layout/PageHeader'
import { buildDerivedAlerts } from '../lib/derivedAlerts'
import { useTraceAnalytics } from '../hooks/useTraceAnalytics'
import { useTraces } from '../hooks/useTraces'
import '../styles/dashboard.css'

const DISMISSED_ALERTS_KEY = 'argusiq-alerts-dismissed'

function readDismissedAlertIds() {
  try {
    const storedValue = sessionStorage.getItem(DISMISSED_ALERTS_KEY)
    const parsedValue = storedValue ? JSON.parse(storedValue) : []

    return Array.isArray(parsedValue) ? parsedValue.filter((value) => typeof value === 'string') : []
  } catch {
    return []
  }
}

function Alerts() {
  const { traces, isLoading, error, websocketStatus, refreshTraces } = useTraces()
  const analytics = useTraceAnalytics(traces)
  const [selectedAlertId, setSelectedAlertId] = useState(null)
  const [dismissedAlertIds, setDismissedAlertIds] = useState(readDismissedAlertIds)

  const derivedAlerts = useMemo(
    () =>
      buildDerivedAlerts({
        traces,
        analytics,
        websocketStatus,
        isLoading,
        error,
      }),
    [analytics, error, isLoading, traces, websocketStatus],
  )

  const visibleAlerts = useMemo(
    () => derivedAlerts.filter((alert) => !dismissedAlertIds.includes(alert.id)),
    [derivedAlerts, dismissedAlertIds],
  )

  const selectedAlert = useMemo(() => {
    if (!selectedAlertId) {
      return null
    }

    return visibleAlerts.find((alert) => alert.id === selectedAlertId) ?? null
  }, [selectedAlertId, visibleAlerts])

  const handleRefresh = useCallback(async () => {
    await refreshTraces()
  }, [refreshTraces])

  const handleAlertSelect = useCallback((alert) => {
    setSelectedAlertId(alert.id)
  }, [])

  const handleAlertDeselect = useCallback(() => {
    setSelectedAlertId(null)
  }, [])

  const handleAlertDismiss = useCallback((alertId) => {
    setDismissedAlertIds((currentIds) => {
      if (currentIds.includes(alertId)) {
        return currentIds
      }

      const nextIds = [...currentIds, alertId]
      sessionStorage.setItem(DISMISSED_ALERTS_KEY, JSON.stringify(nextIds))
      return nextIds
    })
    setSelectedAlertId((currentId) => (currentId === alertId ? null : currentId))
  }, [])

  return (
    <div className="alerts-workspace">
      <section className="alerts-workspace__header" aria-label="Alerts header">
        <PageHeader
          title="Alerts"
          subtitle="Derived signals that may require investigation."
          websocketStatus={websocketStatus}
          isLoading={isLoading}
          onRefresh={handleRefresh}
          showConnectionStatus
          statusNote="Derived from live signals"
        />
      </section>

      <DerivedAlertsBanner />

      <AlertTimeline
        alerts={visibleAlerts}
        error={error}
        isLoading={isLoading}
        onAlertDeselect={handleAlertDeselect}
        onAlertDismiss={handleAlertDismiss}
        onAlertSelect={handleAlertSelect}
        selectedAlert={selectedAlert}
      />
    </div>
  )
}

export default Alerts
