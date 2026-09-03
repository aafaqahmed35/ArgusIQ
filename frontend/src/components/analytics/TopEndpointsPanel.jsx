const SORT_FIELDS = [
  { value: 'traffic', label: 'Requests' },
  { value: 'latency', label: 'Latency' },
]

function TopEndpointsPanel({
  endpoints,
  isLoading = false,
  error = null,
  selectedEndpoint = null,
  onEndpointSelect,
  sortField = 'traffic',
  onSortFieldChange,
  sourceNote = 'Based on the bounded recent trace window',
  formatDuration,
}) {
  const maxCount = Math.max(...endpoints.map((endpoint) => endpoint.requestCount), 1)

  return (
    <section
      className={`analytics-panel analytics-panel--rankings ${isLoading ? 'analytics-panel--loading' : ''}`}
      aria-labelledby="endpoint-rankings-title"
    >
      <div className="analytics-panel__header">
        <div>
          <p className="section-kicker">Traffic</p>
          <h2 id="endpoint-rankings-title">Endpoint Rankings</h2>
          <span className="analytics-panel__source-note">{sourceNote}</span>
        </div>
        <div className="analytics-panel__sort-group">
          {SORT_FIELDS.map((field) => (
            <button
              className={`analytics-panel__sort ${sortField === field.value ? 'is-active' : ''}`}
              type="button"
              key={field.value}
              onClick={() => onSortFieldChange?.(field.value)}
            >
              {field.label}
              {sortField === field.value ? ' ▼' : ''}
            </button>
          ))}
        </div>
      </div>

      {isLoading ? (
        <div className="analytics-skeleton-list" aria-busy="true">
          <span className="skeleton-line skeleton-line--wide" />
          <span className="skeleton-line" />
          <span className="skeleton-line skeleton-line--short" />
        </div>
      ) : error ? (
        <div className="table-state table-state--error" role="alert">
          Unable to load endpoint aggregates from the backend.
        </div>
      ) : endpoints.length === 0 ? (
        <div className="analytics-empty">
          <strong>No endpoint traffic yet</strong>
          <span>Endpoint rankings will appear after traces are persisted.</span>
        </div>
      ) : (
        <ol className="endpoint-list endpoint-list--interactive">
          {endpoints.map((endpoint, index) => {
            const isSelected = selectedEndpoint === endpoint.endpoint
            const handleSelect = () => {
              onEndpointSelect?.(endpoint)
            }

            return (
              <li
                className={`endpoint-list__item ${isSelected ? 'endpoint-list__item--selected' : ''}`}
                key={endpoint.endpoint}
                style={{ '--endpoint-share': `${Math.max((endpoint.requestCount / maxCount) * 100, 4)}%` }}
              >
                <button className="endpoint-list__button" type="button" onClick={handleSelect}>
                  <span className="endpoint-list__rank">{String(index + 1).padStart(2, '0')}</span>
                  <span className="endpoint-list__body">
                    <span className="endpoint-list__path" title={endpoint.endpoint}>
                      {endpoint.endpoint}
                    </span>
                    <span className="endpoint-list__bar" aria-hidden="true" />
                  </span>
                  <span className="endpoint-list__metrics">
                    <strong>{endpoint.requestCount.toLocaleString()}</strong>
                    <small>{formatDuration(endpoint.averageLatencyMs)}</small>
                  </span>
                </button>
              </li>
            )
          })}
        </ol>
      )}
    </section>
  )
}

export default TopEndpointsPanel
