function OverviewRuntimeSummary({
  health,
  backendHealth,
  backendHealthError = null,
  metricsState,
  isLoading = false,
  onRetry,
}) {
  const backendStatus = backendHealth?.status ?? null
  const backendService = backendHealth?.service ?? null
  const runtimeSnapshot = [
    ...health.snapshot,
    {
      label: 'Metrics',
      value:
        metricsState === 'loading'
          ? 'Loading'
          : metricsState === 'error'
            ? 'Unavailable'
            : metricsState === 'empty'
              ? 'No telemetry'
              : 'Available',
      tone: metricsState === 'error' ? 'error' : metricsState === 'loading' ? 'warning' : metricsState === 'ready' ? 'success' : 'neutral',
    },
  ]

  return (
    <section className="analytics-panel overview-runtime-summary" aria-labelledby="overview-runtime-summary-title">
      <div className="analytics-panel__header">
        <div>
          <p className="section-kicker">ArgusIQ + telemetry</p>
          <h2 id="overview-runtime-summary-title">Observed Runtime Signals</h2>
        </div>
        <span className={`overview-runtime-summary__api-state ${backendHealthError ? 'overview-runtime-summary__api-state--error' : ''}`}>
          {backendStatus ?? (backendHealthError ? 'Unavailable' : 'No status')}
        </span>
      </div>

      {isLoading ? (
        <div className="analytics-skeleton-list" aria-busy="true">
          <span className="skeleton-line skeleton-line--wide" />
          <span className="skeleton-line" />
          <span className="skeleton-line skeleton-line--short" />
        </div>
      ) : (
        <div className="overview-runtime-summary__body">
          <div className={`overview-runtime-summary__status overview-runtime-summary__status--${health.tone}`}>
            <strong>{health.status}</strong>
            <p>{health.summary}</p>
            {backendService ? <small>Health endpoint: {backendService}</small> : null}
            {backendHealthError ? (
              <small>The backend health endpoint did not respond. Persisted trace data is shown when available.</small>
            ) : null}
          </div>

          <dl className="overview-runtime-summary__grid">
            {runtimeSnapshot.map((item) => (
              <div className={`overview-runtime-summary__item overview-runtime-summary__item--${item.tone}`} key={item.label}>
                <dt>{item.label}</dt>
                <dd>{item.value}</dd>
              </div>
            ))}
          </dl>

          {(backendHealthError || metricsState === 'error') && onRetry ? (
            <button className="overview-runtime-summary__retry" type="button" onClick={onRetry}>
              Retry unavailable summaries
            </button>
          ) : null}
        </div>
      )}
    </section>
  )
}

export default OverviewRuntimeSummary
