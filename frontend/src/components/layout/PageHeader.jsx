import ConnectionStatusBadge from './ConnectionStatusBadge'

function PageHeader({
  title = 'Command Center',
  subtitle = 'Real-time observability into your services, traces, and system health.',
  websocketStatus,
  isLoading,
  onRefresh,
  showConnectionStatus = true,
  statusNote = null,
}) {
  return (
    <section className="dashboard-header" aria-labelledby="dashboard-title">
      <div className="dashboard-title-block">
        <p className="eyebrow">ArgusIQ Observability</p>
        <h1 id="dashboard-title">{title}</h1>
        <p className="dashboard-subtitle">{subtitle}</p>
      </div>

      <div className="hero-radar" aria-hidden="true">
        <span />
        <span />
        <span />
      </div>

      <div className="dashboard-actions">
        {showConnectionStatus ? <ConnectionStatusBadge status={websocketStatus} /> : null}
        {statusNote ? <span className="page-header__status-note">{statusNote}</span> : null}
        <button className="refresh-button" type="button" onClick={onRefresh} disabled={isLoading}>
          <span>{isLoading ? 'Refreshing...' : 'Refresh'}</span>
          <span className="refresh-button__icon" aria-hidden="true" />
        </button>
      </div>
    </section>
  )
}

export default PageHeader
