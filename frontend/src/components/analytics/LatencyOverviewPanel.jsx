function LatencyOverviewPanel({ averageResponseTime, p95ResponseTime, slowestEndpoint, formatDuration, isLoading = false }) {
  return (
    <section
      className={`analytics-panel analytics-panel--latency ${isLoading ? 'analytics-panel--loading' : ''}`}
      aria-labelledby="latency-overview-title"
    >
      <div className="analytics-panel__header">
        <div>
          <p className="section-kicker">Latency</p>
          <h2 id="latency-overview-title">Latency Overview</h2>
        </div>
        <span className="panel-action">View all</span>
      </div>

      {isLoading ? (
        <div className="analytics-skeleton-list" aria-busy="true">
          <span className="skeleton-line skeleton-line--wide" />
          <span className="skeleton-line" />
          <span className="skeleton-line skeleton-line--short" />
        </div>
      ) : (
        <div className="latency-stack">
          <div className="latency-metrics">
            <div className="latency-row latency-row--primary">
              <span>Average response time</span>
              <strong>{formatDuration(averageResponseTime)}</strong>
            </div>
            <div className="latency-row">
              <span>P95 response time</span>
              <strong>{formatDuration(p95ResponseTime)}</strong>
            </div>
          </div>
          <div className="latency-chart" aria-hidden="true">
            <span />
            <span />
            <span />
            <span />
          </div>
          <div className="latency-row latency-row--endpoint">
            <span>Slowest endpoint</span>
            <strong title={slowestEndpoint?.endpoint}>{slowestEndpoint?.endpoint ?? '—'}</strong>
            <small>
              {slowestEndpoint
                ? `${formatDuration(slowestEndpoint.averageResponseTime)} average across ${slowestEndpoint.count.toLocaleString()} requests`
                : 'No latency data available'}
            </small>
          </div>
        </div>
      )}
    </section>
  )
}

export default LatencyOverviewPanel
