const HTTP_METHODS = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE']
const STATUS_FILTERS = [
  { label: '2xx', value: '2xx', tone: 'success' },
  { label: '4xx', value: '4xx', tone: 'warning' },
  { label: '5xx', value: '5xx', tone: 'error' },
]
const LATENCY_FILTERS = [
  { label: 'Fast', value: 'fast' },
  { label: 'Normal', value: 'normal' },
  { label: 'Slow', value: 'slow' },
  { label: 'Very Slow', value: 'very-slow' },
]

function InvestigationToolbar({ filters, updateFilter, clearFilters, activeFilterCount, availableServices }) {
  const showServiceFilter = availableServices.length > 1

  return (
    <section className="investigation-toolbar" aria-label="Trace investigation filters">
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

      <div className="investigation-toolbar__controls">
        <label className="investigation-control investigation-control--search">
          <span>Endpoint / Path</span>
          <input
            type="search"
            value={filters.search}
            placeholder="Search endpoint or path"
            onChange={(event) => updateFilter('search', event.target.value)}
          />
        </label>

        <label className="investigation-control">
          <span>Method</span>
          <select value={filters.method} onChange={(event) => updateFilter('method', event.target.value)}>
            <option value="all">All methods</option>
            {HTTP_METHODS.map((method) => (
              <option value={method} key={method}>
                {method}
              </option>
            ))}
          </select>
        </label>

        <div className="investigation-control investigation-control--status">
          <span>Status</span>
          <div className="status-filter-group">
            <button
              className={`status-filter status-filter--neutral ${filters.statusCategory === 'all' ? 'is-active' : ''}`}
              type="button"
              onClick={() => updateFilter('statusCategory', 'all')}
            >
              All
            </button>
            {STATUS_FILTERS.map((statusFilter) => (
              <button
                className={`status-filter status-filter--${statusFilter.tone} ${
                  filters.statusCategory === statusFilter.value ? 'is-active' : ''
                }`}
                type="button"
                key={statusFilter.value}
                onClick={() => updateFilter('statusCategory', statusFilter.value)}
              >
                {statusFilter.label}
              </button>
            ))}
          </div>
        </div>

        <label className="investigation-control">
          <span>Latency</span>
          <select
            value={filters.latencyCategory}
            onChange={(event) => updateFilter('latencyCategory', event.target.value)}
          >
            <option value="all">All latency</option>
            {LATENCY_FILTERS.map((latencyFilter) => (
              <option value={latencyFilter.value} key={latencyFilter.value}>
                {latencyFilter.label}
              </option>
            ))}
          </select>
        </label>

        {showServiceFilter ? (
          <label className="investigation-control">
            <span>Service</span>
            <select value={filters.service} onChange={(event) => updateFilter('service', event.target.value)}>
              <option value="all">All services</option>
              {availableServices.map((service) => (
                <option value={service} key={service}>
                  {service}
                </option>
              ))}
            </select>
          </label>
        ) : null}
      </div>
    </section>
  )
}

export default InvestigationToolbar
