import DetectedAnomalies from './DetectedAnomalies'
import HealthSignals from './HealthSignals'
import SystemSnapshot from './SystemSnapshot'
import SystemStatusCard from './SystemStatusCard'

function OperationsCenter({ health, isLoading = false }) {
  return (
    <section className={`operations-center ${isLoading ? 'operations-center--loading' : ''}`} aria-label="Operations Center">
      <div className="operations-center__header">
        <div>
          <p className="section-kicker">Operations Center</p>
          <h2>System Health Center</h2>
        </div>
        <span className="operations-center__badge">Runtime command view</span>
      </div>

      <div className="operations-center__grid">
        {isLoading ? (
          <>
            <div className="operations-skeleton operations-skeleton--status" aria-busy="true">
              <span className="skeleton-line skeleton-line--short" />
              <span className="skeleton-line skeleton-line--wide" />
              <span className="skeleton-line" />
            </div>
            <div className="operations-skeleton operations-skeleton--snapshot" aria-busy="true">
              <span className="skeleton-line skeleton-line--wide" />
              <span className="skeleton-line" />
              <span className="skeleton-line skeleton-line--short" />
            </div>
            <div className="operations-skeleton operations-skeleton--signals" aria-busy="true">
              <span className="skeleton-line skeleton-line--wide" />
              <span className="skeleton-line" />
              <span className="skeleton-line skeleton-line--short" />
            </div>
          </>
        ) : (
          <>
            <SystemStatusCard status={health.status} tone={health.tone} summary={health.summary} />
            <SystemSnapshot items={health.snapshot} />
            <HealthSignals signals={health.signals} />
            <DetectedAnomalies anomalies={health.anomalies} />
          </>
        )}
      </div>
    </section>
  )
}

export default OperationsCenter
