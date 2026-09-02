import { Link } from 'react-router-dom'
import { formatDuration } from '../../lib/traceAggregation'

function formatTrafficShare(value) {
  if (value === null || value === undefined) {
    return '—'
  }

  return `${value.toLocaleString(undefined, { maximumFractionDigits: 1 })}%`
}

function ServiceGroupDetail({ group, totalRequests, recentTraceLimit }) {
  if (!group) {
    return (
      <section className="analytics-panel service-group-detail" aria-labelledby="service-group-detail-title">
        <div className="analytics-panel__header">
          <div>
            <p className="section-kicker">Service Groups</p>
            <h2 id="service-group-detail-title">Group Detail</h2>
          </div>
        </div>
        <div className="service-group-detail__empty table-state table-state--rich">
          <strong>No service group selected</strong>
          <span>Select a service group from the table to inspect traffic and latency metrics.</span>
        </div>
      </section>
    )
  }

  return (
    <section className="analytics-panel service-group-detail" aria-labelledby="service-group-detail-title">
      <div className="analytics-panel__header">
        <div>
          <p className="section-kicker">Service Groups</p>
          <h2 id="service-group-detail-title">Group Detail</h2>
        </div>
        <div className="service-group-detail__actions">
          <Link className="panel-action service-group-detail__action" to="/analytics">
            Investigate Endpoints →
          </Link>
          <Link className="panel-action service-group-detail__action" to="/traces">
            Investigate Requests →
          </Link>
        </div>
      </div>

      <dl className="analytics-endpoint-detail__grid">
        <div className="analytics-endpoint-detail__item">
          <dt>Service Group</dt>
          <dd className="analytics-endpoint-detail__path" title={group.name}>
            {group.name}
          </dd>
        </div>
        <div className="analytics-endpoint-detail__item">
          <dt>Traffic %</dt>
          <dd>{formatTrafficShare(group.trafficShare)}</dd>
        </div>
        <div className="analytics-endpoint-detail__item">
          <dt>Requests</dt>
          <dd>{group.requestCount.toLocaleString()}</dd>
        </div>
        <div className="analytics-endpoint-detail__item">
          <dt>Average RT</dt>
          <dd>{formatDuration(group.averageResponseTime)}</dd>
        </div>
        <div className="analytics-endpoint-detail__item">
          <dt>P95</dt>
          <dd>{formatDuration(group.p95ResponseTime)}</dd>
        </div>
        <div className="analytics-endpoint-detail__item">
          <dt>Endpoints</dt>
          <dd>{group.endpointCount.toLocaleString()}</dd>
        </div>
      </dl>

      <div className="service-group-detail__endpoints">
        <h3>Endpoints in this group</h3>
        {group.endpoints.length === 0 ? (
          <p className="service-group-detail__endpoints-empty">No endpoints in the recent trace window for this group.</p>
        ) : (
          <ol className="service-group-detail__endpoint-list">
            {group.endpoints.map((endpoint) => (
              <li className="service-group-detail__endpoint-item" key={endpoint.endpoint}>
                <span className="service-group-detail__endpoint-path" title={endpoint.endpoint}>
                  {endpoint.endpoint}
                </span>
                <span className="service-group-detail__endpoint-metrics">
                  {endpoint.count.toLocaleString()} req · {formatDuration(endpoint.averageResponseTime)} avg
                </span>
              </li>
            ))}
          </ol>
        )}
      </div>

      <p className="analytics-endpoint-detail__caption">
        Metrics computed from {Number(totalRequests).toLocaleString()} recent trace records (limit {recentTraceLimit})
      </p>
    </section>
  )
}

export default ServiceGroupDetail
