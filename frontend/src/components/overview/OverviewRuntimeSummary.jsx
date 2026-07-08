function OverviewRuntimeSummary({ health, backendHealth, isLoading = false }) {
  const backendStatus = backendHealth?.status ?? null
  const backendService = backendHealth?.service ?? null

  return (
    <section className="analytics-panel overview-runtime-summary" aria-labelledby="overview-runtime-summary-title">
      <div className="analytics-panel__header">
        <div>
          <p className="section-kicker">Runtime</p>
          <h2 id="overview-runtime-summary-title">Runtime Summary</h2>
        </div>
        {backendStatus ? <span className="panel-action">{backendStatus}</span> : null}
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
            {backendService ? <small>Backend service: {backendService}</small> : null}
          </div>

          <dl className="overview-runtime-summary__grid">
            {health.snapshot.map((item) => (
              <div className={`overview-runtime-summary__item overview-runtime-summary__item--${item.tone}`} key={item.label}>
                <dt>{item.label}</dt>
                <dd>{item.value}</dd>
              </div>
            ))}
          </dl>
        </div>
      )}
    </section>
  )
}

export default OverviewRuntimeSummary
