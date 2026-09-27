import ConnectionStatusBadge from './ConnectionStatusBadge'

function PageHeader({
  title = 'Command Center',
  subtitle = 'Real-time observability into your services, traces, and system health.',
  eyebrow = 'ArgusIQ Observability',
  websocketStatus,
  isLoading,
  onRefresh,
  actions = null,
  showConnectionStatus = true,
  statusNote = null,
}) {
  return (
    <header className="dashboard-header">
      <div className="dashboard-title-block">
        {eyebrow ? <p className="eyebrow">{eyebrow}</p> : null}
        <h1>{title}</h1>
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
        {actions}
        <button className="refresh-button" type="button" onClick={onRefresh} disabled={isLoading}>
          <span>{isLoading ? 'Refreshing...' : 'Refresh'}</span>
          <span className="refresh-button__icon" aria-hidden="true" />
        </button>
      </div>
    </header>
  )
}

export default PageHeader
