import TraceTable from '../TraceTable'

function TracePanel({ traces, isLoading, error, onTraceSelect, activeFilterCount = 0, onClearFilters }) {
  const hasNoFilterMatches = !isLoading && !error && activeFilterCount > 0 && traces.length === 0

  return (
    <section className="trace-panel" aria-label="Trace records">
      <div className="trace-panel__header">
        <div>
          <p className="section-kicker">Request stream</p>
          <h2>Recent Traces</h2>
        </div>
        <span className="trace-panel__count">{traces.length.toLocaleString()} records</span>
      </div>

      {hasNoFilterMatches ? (
        <div className="table-state table-state--filtered">
          <p>No traces match your current filters.</p>
          <button className="investigation-toolbar__clear" type="button" onClick={onClearFilters}>
            Clear Filters
          </button>
        </div>
      ) : (
        <TraceTable traces={traces} isLoading={isLoading} error={error} onTraceSelect={onTraceSelect} />
      )}
    </section>
  )
}

export default TracePanel
