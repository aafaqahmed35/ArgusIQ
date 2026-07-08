import DetectedAnomalies from './DetectedAnomalies'
import HealthSignals from './HealthSignals'
import SystemSnapshot from './SystemSnapshot'
import SystemStatusCard from './SystemStatusCard'

function OperationsCenter({ health }) {
  return (
    <section className="operations-center" aria-label="Operations Center">
      <div className="operations-center__header">
        <div>
          <p className="section-kicker">Operations Center</p>
          <h2>System Health Center</h2>
        </div>
      </div>

      <div className="operations-center__grid">
        <SystemStatusCard status={health.status} tone={health.tone} summary={health.summary} />
        <SystemSnapshot items={health.snapshot} />
        <HealthSignals signals={health.signals} />
        <DetectedAnomalies anomalies={health.anomalies} />
      </div>
    </section>
  )
}

export default OperationsCenter
