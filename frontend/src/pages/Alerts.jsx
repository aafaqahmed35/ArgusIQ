import { useEffect, useState, useSyncExternalStore } from 'react'
import AlertsEvidence from '../components/alerts/AlertsEvidence'
import { useTraces } from '../hooks/useTraces'
import { acknowledgeAlert, createAlertRule, fetchAlertRules, fetchAlerts, resolveAlert } from '../services/traceApi'
import { createAlertInvestigationStore } from './alertInvestigation'
import '../styles/dashboard.css'
import '../styles/evidence.css'
import '../styles/alerts.css'

function Alerts() {
  const { websocketStatus } = useTraces()
  const [store] = useState(() => createAlertInvestigationStore({ acknowledgeAlert, createAlertRule, fetchAlertRules, fetchAlerts, resolveAlert }))
  const state = useSyncExternalStore(store.subscribe, store.getSnapshot, store.getSnapshot)
  const [selectedAlertId, setSelectedAlertId] = useState(null)
  useEffect(() => {
    queueMicrotask(store.refresh)
    return store.dispose
  }, [store])
  return <AlertsEvidence state={state} websocketStatus={websocketStatus} selectedAlertId={selectedAlertId} onSelect={setSelectedAlertId} onRefresh={store.refresh} onAction={store.mutate} onCreateRule={store.createRule} />
}

export default Alerts
