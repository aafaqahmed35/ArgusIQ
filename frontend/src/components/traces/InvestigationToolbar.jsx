const HTTP_METHODS = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE']
const TRACE_STATUSES = ['OK', 'ERROR', 'UNSET']
const LATENCY_FILTERS = [
  { label: 'Fast (<100 ms)', value: 'fast' },
  { label: 'Normal (100–499 ms)', value: 'normal' },
  { label: 'Slow (500–999 ms)', value: 'slow' },
  { label: 'Very slow (≥1000 ms)', value: 'very-slow' },
]
const SORT_FIELDS = [
  { label: 'Start time', value: 'startTime' },
  { label: 'Duration', value: 'durationMs' },
  { label: 'Service', value: 'serviceName' },
  { label: 'Status', value: 'statusCode' },
  { label: 'HTTP method', value: 'httpMethod' },
  { label: 'Endpoint', value: 'requestUri' },
  { label: 'Trace ID', value: 'traceId' },
]

function InvestigationToolbar({
  query,
  searchDraft,
  onSearchDraftChange,
  updateQuery,
  clearFilters,
  activeFilterCount,
}) {
  return (
    <section className="investigation-toolbar investigation-toolbar--explorer" aria-label="Trace investigation filters">
      <div className="investigation-toolbar__header">
        <div>
          <p className="section-kicker">Investigation</p>
          <h2>Trace Search</h2>
        </div>
        <div className="investigation-toolbar__actions">
          <span className="investigation-toolbar__count">{activeFilterCount} active</span>
          <button
            className="investigation-toolbar__clear"
            type="button"
            onClick={clearFilters}
            disabled={activeFilterCount === 0}
          >
            Clear Filters
          </button>
        </div>
      </div>

      <div className="investigation-toolbar__controls investigation-toolbar__controls--query">
        <label className="investigation-control investigation-control--search">
          <span>Search</span>
          <input
            type="search"
            value={searchDraft}
            placeholder="Trace, service, operation, endpoint, method, or status"
            onChange={(event) => onSearchDraftChange(event.target.value)}
          />
        </label>

        <label className="investigation-control">
          <span>Service</span>
          <input
            type="text"
            value={query.service}
            placeholder="Exact service name"
            onChange={(event) => updateQuery({ service: event.target.value })}
          />
        </label>

        <label className="investigation-control">
          <span>Endpoint</span>
          <input
            type="text"
            value={query.endpoint}
            placeholder="Path contains…"
            onChange={(event) => updateQuery({ endpoint: event.target.value })}
          />
        </label>

        <label className="investigation-control">
          <span>Method</span>
          <select value={query.httpMethod} onChange={(event) => updateQuery({ httpMethod: event.target.value })}>
            <option value="">All methods</option>
            {HTTP_METHODS.map((method) => <option value={method} key={method}>{method}</option>)}
          </select>
        </label>

        <label className="investigation-control">
          <span>Status</span>
          <select value={query.status} onChange={(event) => updateQuery({ status: event.target.value })}>
            <option value="">All statuses</option>
            {TRACE_STATUSES.map((status) => <option value={status} key={status}>{status}</option>)}
          </select>
        </label>

        <label className="investigation-control">
          <span>Latency</span>
          <select value={query.latency} onChange={(event) => updateQuery({ latency: event.target.value })}>
            <option value="">All latency</option>
            {LATENCY_FILTERS.map((filter) => <option value={filter.value} key={filter.value}>{filter.label}</option>)}
          </select>
        </label>

        <label className="investigation-control">
          <span>Trace ID</span>
          <input
            type="text"
            value={query.traceId}
            placeholder="Exact trace ID"
            onChange={(event) => updateQuery({ traceId: event.target.value })}
          />
        </label>

        <label className="investigation-control">
          <span>Span ID</span>
          <input
            type="text"
            value={query.spanId}
            placeholder="Exact span ID"
            onChange={(event) => updateQuery({ spanId: event.target.value })}
          />
        </label>

        <label className="investigation-control">
          <span>Started after (UTC)</span>
          <input type="datetime-local" value={query.from} onChange={(event) => updateQuery({ from: event.target.value })} />
        </label>

        <label className="investigation-control">
          <span>Started before (UTC)</span>
          <input type="datetime-local" value={query.to} onChange={(event) => updateQuery({ to: event.target.value })} />
        </label>

        <label className="investigation-control">
          <span>Sort field</span>
          <select value={query.sortBy} onChange={(event) => updateQuery({ sortBy: event.target.value })}>
            {SORT_FIELDS.map((field) => <option value={field.value} key={field.value}>{field.label}</option>)}
          </select>
        </label>

        <label className="investigation-control">
          <span>Direction</span>
          <select value={query.sortDirection} onChange={(event) => updateQuery({ sortDirection: event.target.value })}>
            <option value="desc">Descending</option>
            <option value="asc">Ascending</option>
          </select>
        </label>
      </div>
    </section>
  )
}

export default InvestigationToolbar
