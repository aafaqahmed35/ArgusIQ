const HTTP_METHODS = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE']
const LATENCY_FILTERS = [
  { label: 'Fast', value: 'fast' },
  { label: 'Normal', value: 'normal' },
  { label: 'Slow', value: 'slow' },
  { label: 'Very Slow', value: 'very-slow' },
]

const INVESTIGATION_MODES = [
  {
    label: 'All traces',
    value: 'all',
    description: 'Loaded from GET /api/v1/traces',
  },
  {
    label: 'Slow traces',
    value: 'slow',
    description: 'Loaded from GET /api/v1/traces/slow',
  },
]

function InvestigationToolbar({
  filters,
  updateFilter,
  clearFilters,
  activeFilterCount,
  investigationMode = 'all',
  onInvestigationModeChange,
}) {
  const activeMode = INVESTIGATION_MODES.find((mode) => mode.value === investigationMode) ?? INVESTIGATION_MODES[0]

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

      <div className="investigation-toolbar__controls">
        <div className="investigation-control investigation-control--mode">
          <span>Investigation mode</span>
          <div className="status-filter-group">
            {INVESTIGATION_MODES.map((mode) => (
              <button
                className={`status-filter status-filter--neutral ${
                  investigationMode === mode.value ? 'is-active' : ''
                }`}
                type="button"
                key={mode.value}
                onClick={() => onInvestigationModeChange?.(mode.value)}
              >
                {mode.label}
              </button>
            ))}
          </div>
          <small className="investigation-toolbar__mode-note">{activeMode.description}</small>
        </div>

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
      </div>
    </section>
  )
}

export default InvestigationToolbar
