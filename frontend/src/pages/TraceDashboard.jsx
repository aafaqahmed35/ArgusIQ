import TraceTable from '../components/TraceTable'
import { useTraces } from '../hooks/useTraces'

function TraceDashboard() {
  const { traces, isLoading, error, websocketStatus, refreshTraces } = useTraces()

  return (
    <main className="dashboard-page">
      <section className="dashboard-header" aria-labelledby="dashboard-title">
        <div>
          <p className="eyebrow">ArgusIQ Observability</p>
          <h1 id="dashboard-title">Trace Dashboard</h1>
          <p className="dashboard-subtitle">
            REST-hydrated request trace monitoring with live websocket updates.
          </p>
        </div>

        <div className="dashboard-actions">
          <span className={`connection-status connection-status--${websocketStatus.toLowerCase()}`}>
            {websocketStatus}
          </span>
          <button className="refresh-button" type="button" onClick={refreshTraces} disabled={isLoading}>
            {isLoading ? 'Refreshing...' : 'Refresh'}
          </button>
        </div>
      </section>

      <section className="summary-grid" aria-label="Trace summary">
        <div className="summary-tile">
          <span>Total Traces</span>
          <strong>{traces.length}</strong>
        </div>
        <div className="summary-tile">
          <span>Data Source</span>
          <strong>REST + WS</strong>
        </div>
        <div className="summary-tile">
          <span>Realtime</span>
          <strong>{websocketStatus}</strong>
        </div>
      </section>

      <section className="dashboard-panel" aria-label="Trace records">
        <div className="panel-header">
          <div>
            <h2>Recent Traces</h2>
            <p>Initial data from REST. New traces stream from /topic/traces.</p>
          </div>
        </div>

        <TraceTable traces={traces} isLoading={isLoading} error={error} />
      </section>
    </main>
  )
}

export default TraceDashboard
