import { useRef } from 'react'
import { formatDuration } from '../../lib/traceAggregation'

const SORT_FIELDS = [
  { value: 'traffic', label: 'Requests' },
  { value: 'latency', label: 'Latency' },
]

function formatPercent(value) {
  return value === null || value === undefined ? '—' : `${value.toFixed(1)}%`
}

function ServiceListPanel({
  services,
  isLoading = false,
  error = null,
  selectedServiceId = null,
  onServiceSelect,
  sortField = 'traffic',
  onSortFieldChange,
}) {
  const rowRefs = useRef([])

  const handleRowKeyDown = (event, service, index) => {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault()
      onServiceSelect?.(service)
      return
    }

    const nextIndex = event.key === 'ArrowDown' ? index + 1 : event.key === 'ArrowUp' ? index - 1 : null
    if (nextIndex !== null && services[nextIndex]) {
      event.preventDefault()
      onServiceSelect?.(services[nextIndex])
      rowRefs.current[nextIndex]?.focus()
    }
  }

  return (
    <section className={`analytics-panel service-groups-panel ${isLoading ? 'analytics-panel--loading' : ''}`} aria-labelledby="services-title">
      <div className="analytics-panel__header">
        <div>
          <p className="section-kicker">OpenTelemetry services</p>
          <h2 id="services-title">Observed Services</h2>
          <span className="analytics-panel__source-note">
            {services.length.toLocaleString()} discovered service identities
          </span>
        </div>
        <div className="analytics-panel__sort-group">
          {SORT_FIELDS.map((field) => (
            <button
              className={`analytics-panel__sort ${sortField === field.value ? 'is-active' : ''}`}
              type="button"
              key={field.value}
              onClick={() => onSortFieldChange?.(field.value)}
            >
              {field.label}{sortField === field.value ? ' ▼' : ''}
            </button>
          ))}
        </div>
      </div>

      {isLoading ? (
        <div className="table-state table-state--skeleton" role="status" aria-busy="true">
          <span className="skeleton-line skeleton-line--wide" />
          <span className="skeleton-line" />
          <span className="skeleton-line skeleton-line--short" />
        </div>
      ) : error ? (
        <div className="table-state table-state--error" role="alert">Unable to load service aggregates.</div>
      ) : services.length === 0 ? (
        <div className="analytics-empty">
          <strong>No services observed</strong>
          <span>Services appear after telemetry containing service.name is ingested.</span>
        </div>
      ) : (
        <div className="table-shell service-groups-panel__table-shell">
          <table className="trace-table service-groups-table">
            <thead>
              <tr>
                <th scope="col">Service</th>
                <th scope="col">Telemetry</th>
                <th scope="col">Persisted requests</th>
                <th scope="col">Last 1 min</th>
                <th scope="col">Average</th>
                <th scope="col">P95</th>
                <th scope="col">Observed success</th>
              </tr>
            </thead>
            <tbody>
              {services.map((service, index) => (
                <tr
                  className={`trace-table__row trace-table__row--interactive ${selectedServiceId === service.id ? 'trace-table__row--selected' : ''}`}
                  key={service.id}
                  ref={(element) => { rowRefs.current[index] = element }}
                  onClick={() => onServiceSelect?.(service)}
                  onKeyDown={(event) => handleRowKeyDown(event, service, index)}
                  role="button"
                  tabIndex={0}
                >
                  <td className="cell-strong cell-path" title={service.serviceName}>{service.serviceName}</td>
                  <td>{service.telemetryStatus}</td>
                  <td>{service.requestCount.toLocaleString()}</td>
                  <td>{service.requestsPerMinute.toLocaleString()}</td>
                  <td>{formatDuration(service.averageLatencyMs)}</td>
                  <td>{formatDuration(service.p95LatencyMs)}</td>
                  <td>{formatPercent(service.successRate)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}

export default ServiceListPanel
