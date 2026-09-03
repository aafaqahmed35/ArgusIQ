import { Link } from 'react-router-dom'
import { formatDuration } from '../../lib/traceAggregation'

function formatPercent(value) {
  return value === null || value === undefined ? '—' : `${value.toFixed(1)}%`
}

function formatTimestamp(value) {
  if (!value) return '—'
  return new Intl.DateTimeFormat('en', { dateStyle: 'medium', timeStyle: 'medium', timeZone: 'UTC' }).format(new Date(value)) + ' UTC'
}

function formatOperation(operation) {
  if (!operation) return '—'
  return `${operation.operationName} · ${formatDuration(operation.averageLatencyMs)} avg · ${operation.observationCount.toLocaleString()} spans`
}

function ServiceDetailPanel({ service, isLoading = false, error = null }) {
  if (isLoading) {
    return <section className="analytics-panel service-group-detail table-state table-state--skeleton" aria-busy="true">Loading service detail…</section>
  }

  if (error) {
    return <section className="analytics-panel service-group-detail table-state table-state--error" role="alert">Unable to load service detail.</section>
  }

  if (!service) {
    return (
      <section className="analytics-panel service-group-detail" aria-labelledby="service-detail-title">
        <div className="analytics-panel__header"><div><p className="section-kicker">Service identity</p><h2 id="service-detail-title">Service Detail</h2></div></div>
        <div className="service-group-detail__empty table-state table-state--rich">
          <strong>No service selected</strong>
          <span>Select a discovered service to inspect its observed request and operation evidence.</span>
        </div>
      </section>
    )
  }

  return (
    <section className="analytics-panel service-group-detail" aria-labelledby="service-detail-title">
      <div className="analytics-panel__header">
        <div><p className="section-kicker">Service identity</p><h2 id="service-detail-title">{service.serviceName}</h2></div>
        <Link className="panel-action service-group-detail__action" to={`/traces?service=${encodeURIComponent(service.serviceName)}`}>Investigate traces →</Link>
      </div>

      <dl className="analytics-endpoint-detail__grid">
        <div className="analytics-endpoint-detail__item"><dt>Telemetry status</dt><dd>{service.telemetryStatus}</dd></div>
        <div className="analytics-endpoint-detail__item"><dt>Persisted requests</dt><dd>{service.requestCount.toLocaleString()}</dd></div>
        <div className="analytics-endpoint-detail__item"><dt>Requests in last minute</dt><dd>{service.requestsPerMinute.toLocaleString()}</dd></div>
        <div className="analytics-endpoint-detail__item"><dt>Errors</dt><dd>{service.errorCount.toLocaleString()} · {formatPercent(service.errorRate)}</dd></div>
        <div className="analytics-endpoint-detail__item"><dt>Observed success rate</dt><dd>{formatPercent(service.successRate)}</dd></div>
        <div className="analytics-endpoint-detail__item"><dt>Average / P95 / P99</dt><dd>{formatDuration(service.averageLatencyMs)} / {formatDuration(service.p95LatencyMs)} / {formatDuration(service.p99LatencyMs)}</dd></div>
        <div className="analytics-endpoint-detail__item"><dt>Observed operations</dt><dd>{service.observedOperationCount.toLocaleString()}</dd></div>
        <div className="analytics-endpoint-detail__item"><dt>Outgoing dependencies</dt><dd>{service.dependencyCount.toLocaleString()}</dd></div>
        <div className="analytics-endpoint-detail__item"><dt>First observed</dt><dd>{formatTimestamp(service.firstSeen)}</dd></div>
        <div className="analytics-endpoint-detail__item"><dt>Last observed</dt><dd>{formatTimestamp(service.lastSeen)}</dd></div>
        <div className="analytics-endpoint-detail__item"><dt>Observation age</dt><dd>{service.observationAgeMinutes === null ? '—' : `${service.observationAgeMinutes.toLocaleString()} min`}</dd></div>
        <div className="analytics-endpoint-detail__item"><dt>Runtime metadata</dt><dd>{[service.environment, service.version, service.language].filter(Boolean).join(' · ') || 'Not observed'}</dd></div>
        <div className="analytics-endpoint-detail__item"><dt>Slowest operation</dt><dd>{formatOperation(service.slowestOperation)}</dd></div>
        <div className="analytics-endpoint-detail__item"><dt>Fastest operation</dt><dd>{formatOperation(service.fastestOperation)}</dd></div>
      </dl>

      <div className="service-group-detail__endpoints">
        <h3>Top operations by observed span count</h3>
        {service.topOperationsByTraffic.length === 0 ? (
          <p className="service-group-detail__endpoints-empty">No operation spans observed for this service.</p>
        ) : (
          <ol className="service-group-detail__endpoint-list">
            {service.topOperationsByTraffic.map((operation) => (
              <li className="service-group-detail__endpoint-item" key={operation.operationName}>
                <span className="service-group-detail__endpoint-path">{operation.operationName}</span>
                <span className="service-group-detail__endpoint-metrics">{operation.observationCount.toLocaleString()} spans · {formatDuration(operation.averageLatencyMs)} avg</span>
              </li>
            ))}
          </ol>
        )}
      </div>

      <p className="analytics-endpoint-detail__caption">
        Recent evidence: {service.recentTraces.length.toLocaleString()} traces · {service.recentErrors.length.toLocaleString()} error traces. Telemetry status is observational, not a liveness or availability claim.
      </p>
    </section>
  )
}

export default ServiceDetailPanel
