const FILTER_MODES = [
  { id: 'all', label: 'All spans' },
  { id: 'errors', label: 'Errors only' },
  { id: 'critical', label: 'Critical path' },
  { id: 'db', label: 'Database' },
  { id: 'http', label: 'HTTP' },
]

function SpanFilterToolbar({
  searchQuery,
  onSearchChange,
  filterMode,
  onFilterModeChange,
  serviceFilter,
  onServiceFilterChange,
  availableServices = [],
}) {
  return (
    <div className="span-filter-toolbar" aria-label="Span view filters">
      <label className="span-filter-toolbar__search">
        <span>Find spans</span>
        <span>
          <input
            type="search"
            value={searchQuery}
            onChange={(event) => onSearchChange(event.target.value)}
            placeholder="Name, service, span ID, or status"
          />
          {searchQuery ? (
            <button type="button" onClick={() => onSearchChange('')} aria-label="Clear span search">Clear</button>
          ) : null}
        </span>
      </label>

      {availableServices.length > 1 ? (
        <label className="span-filter-toolbar__service">
          <span>Service</span>
          <select value={serviceFilter} onChange={(event) => onServiceFilterChange(event.target.value)}>
            <option value="all">All services ({availableServices.length})</option>
            {availableServices.map((service) => <option key={service} value={service}>{service}</option>)}
          </select>
        </label>
      ) : null}

      <div className="span-filter-toolbar__modes" role="group" aria-label="Span type">
        {FILTER_MODES.map((mode) => (
          <button
            key={mode.id}
            type="button"
            className={filterMode === mode.id ? 'is-active' : ''}
            aria-pressed={filterMode === mode.id}
            onClick={() => onFilterModeChange(mode.id)}
          >
            {mode.label}
          </button>
        ))}
      </div>
    </div>
  )
}

export default SpanFilterToolbar
