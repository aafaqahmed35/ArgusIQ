function LatencyOverviewPanel({ averageResponseTime, p95ResponseTime, slowestEndpoint, formatDuration }) {
  return (
    <section className="analytics-panel" aria-labelledby="latency-overview-title">
      <div className="analytics-panel__header">
        <div>
          <p className="section-kicker">Latency</p>
          <h2 id="latency-overview-title">Latency Overview</h2>
        </div>
      </div>

      <div className="latency-stack">
        <div className="latency-row">
          <span>Average response time</span>
          <strong>{formatDuration(averageResponseTime)}</strong>
        </div>
        <div className="latency-row">
          <span>P95 response time</span>
          <strong>{formatDuration(p95ResponseTime)}</strong>
        </div>
        <div className="latency-row latency-row--endpoint">
          <span>Slowest endpoint</span>
          <strong>{slowestEndpoint?.endpoint ?? '—'}</strong>
          <small>
            {slowestEndpoint
              ? `${formatDuration(slowestEndpoint.averageResponseTime)} average across ${slowestEndpoint.count.toLocaleString()} requests`
              : 'No latency data available'}
          </small>
        </div>
      </div>
    </section>
  )
}

export default LatencyOverviewPanel
