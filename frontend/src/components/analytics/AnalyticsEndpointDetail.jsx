import { Link } from 'react-router-dom'

function AnalyticsEndpointDetail({ endpoint, totalTraces, formatDuration }) {
  if (!endpoint) {
    return (
      <section className="analytics-panel analytics-endpoint-detail" aria-labelledby="analytics-endpoint-detail-title">
        <div className="analytics-panel__header">
          <div>
            <p className="section-kicker">Endpoint analysis</p>
            <h2 id="analytics-endpoint-detail-title">Endpoint Detail</h2>
          </div>
        </div>
        <div className="analytics-endpoint-detail__empty table-state table-state--rich">
          <strong>No endpoint selected</strong>
          <span>Select an endpoint from the rankings to inspect latency statistics.</span>
        </div>
      </section>
    )
  }

  const contribution =
    totalTraces > 0 ? `${Math.round((endpoint.requestCount / totalTraces) * 100).toLocaleString()}%` : '—'

  return (
    <section className="analytics-panel analytics-endpoint-detail" aria-labelledby="analytics-endpoint-detail-title">
      <div className="analytics-panel__header">
        <div>
          <p className="section-kicker">Endpoint analysis</p>
          <h2 id="analytics-endpoint-detail-title">Endpoint Detail</h2>
        </div>
        <Link
          className="panel-action analytics-endpoint-detail__action"
          to={`/traces?endpoint=${encodeURIComponent(endpoint.endpoint)}`}
        >
          Open in Trace Explorer →
        </Link>
      </div>

      <dl className="analytics-endpoint-detail__grid">
        <div className="analytics-endpoint-detail__item">
          <dt>Endpoint</dt>
          <dd className="analytics-endpoint-detail__path" title={endpoint.endpoint}>
            {endpoint.endpoint}
          </dd>
        </div>
        <div className="analytics-endpoint-detail__item">
          <dt>Requests</dt>
          <dd>{endpoint.requestCount.toLocaleString()}</dd>
        </div>
        <div className="analytics-endpoint-detail__item">
          <dt>Average</dt>
          <dd>{formatDuration(endpoint.averageLatencyMs)}</dd>
        </div>
        <div className="analytics-endpoint-detail__item">
          <dt>P95</dt>
          <dd>{formatDuration(endpoint.p95LatencyMs)}</dd>
        </div>
        <div className="analytics-endpoint-detail__item">
          <dt>Errors</dt>
          <dd>{endpoint.errorCount.toLocaleString()}</dd>
        </div>
        <div className="analytics-endpoint-detail__item">
          <dt>Error rate</dt>
          <dd>{endpoint.errorRate === null ? '—' : `${endpoint.errorRate.toFixed(1)}%`}</dd>
        </div>
        <div className="analytics-endpoint-detail__item">
          <dt>Contribution</dt>
          <dd>{contribution}</dd>
        </div>
      </dl>
      <p className="analytics-endpoint-detail__caption">Metrics computed from all persisted traces for this endpoint</p>
    </section>
  )
}

export default AnalyticsEndpointDetail
