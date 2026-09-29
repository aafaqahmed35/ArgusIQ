function OverviewPanelState({
  state,
  loadingLabel = 'Loading telemetry',
  errorTitle = 'Telemetry unavailable',
  errorMessage = 'ArgusIQ could not load this persisted summary.',
  emptyTitle = 'No telemetry',
  emptyMessage = 'This view will populate after trace data is persisted.',
  onRetry,
  children,
}) {
  if (state === 'loading') {
    return (
      <div className="overview-panel-state overview-panel-state--loading" aria-busy="true" aria-label={loadingLabel}>
        <span className="skeleton-line skeleton-line--wide" />
        <span className="skeleton-line" />
        <span className="skeleton-line skeleton-line--short" />
      </div>
    )
  }

  if (state === 'error') {
    return (
      <div className="overview-panel-state overview-panel-state--error" role="alert">
        <strong>{errorTitle}</strong>
        <span>{errorMessage}</span>
        {onRetry ? (
          <button className="overview-panel-state__action" type="button" onClick={onRetry}>
            Retry
          </button>
        ) : null}
      </div>
    )
  }

  if (state === 'empty') {
    return (
      <div className="overview-panel-state overview-panel-state--empty">
        <strong>{emptyTitle}</strong>
        <span>{emptyMessage}</span>
      </div>
    )
  }

  return children
}

export default OverviewPanelState
