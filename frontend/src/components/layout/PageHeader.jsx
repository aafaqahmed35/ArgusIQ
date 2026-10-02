import ConnectionStatusBadge from './ConnectionStatusBadge'

function PageHeader({
  title = 'ArgusIQ',
  subtitle = 'Investigate observed backend telemetry.',
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

      <div className="dashboard-actions">
        {showConnectionStatus ? <ConnectionStatusBadge status={websocketStatus} /> : null}
        {statusNote ? <span className="page-header__status-note">{statusNote}</span> : null}
        {actions}
        {onRefresh ? <button className="refresh-button" type="button" onClick={onRefresh} disabled={isLoading}>
          <span>{isLoading ? 'Refreshing...' : 'Refresh'}</span>
          <span className="refresh-button__icon" aria-hidden="true" />
        </button> : null}
      </div>
    </header>
  )
}

export default PageHeader
