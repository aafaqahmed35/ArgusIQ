import ConnectionStatusBadge from './ConnectionStatusBadge'

function PageHeader({ websocketStatus, isLoading, onRefresh }) {
  return (
    <section className="dashboard-header" aria-labelledby="dashboard-title">
      <div className="dashboard-title-block">
        <p className="eyebrow">ArgusIQ Observability</p>
        <h1 id="dashboard-title">Trace Dashboard</h1>
        <p className="dashboard-subtitle">
          Real-time service telemetry with REST hydration, live trace intake, and response-time visibility.
        </p>
      </div>

      <div className="dashboard-actions">
        <ConnectionStatusBadge status={websocketStatus} />
        <button className="refresh-button" type="button" onClick={onRefresh} disabled={isLoading}>
          {isLoading ? 'Refreshing...' : 'Refresh'}
        </button>
      </div>
    </section>
  )
}

export default PageHeader
