import TraceTable from '../TraceTable'
import { getTraceKey } from '../../hooks/useTraces'

function TracePanel({
  traces,
  isLoading,
  error,
  onTraceSelect,
  activeFilterCount = 0,
  onClearFilters,
  selectedTrace = null,
  highlightedTraceKeys = [],
  sourceLabel = 'Showing all loaded traces',
  emptyTitle = 'No traces available',
  emptyMessage = 'Trace records will appear here as soon as the frontend receives telemetry.',
}) {
  const hasNoFilterMatches = !isLoading && !error && activeFilterCount > 0 && traces.length === 0
  const selectedTraceKey = selectedTrace ? getTraceKey(selectedTrace) : null
  const highlightedKeySet = new Set(highlightedTraceKeys)

  return (
    <section className="trace-panel" aria-label="Trace records">
      <div className="trace-panel__header">
        <div>
          <p className="section-kicker">Request stream</p>
          <h2>Recent Traces</h2>
        </div>
        <div className="trace-panel__meta">
          <span className="trace-panel__source">{sourceLabel}</span>
          <span className="trace-panel__count">{traces.length.toLocaleString()} records</span>
        </div>
      </div>

      {hasNoFilterMatches ? (
        <div className="table-state table-state--filtered table-state--rich">
          <strong>No traces match your current filters.</strong>
          <p>Broaden the investigation criteria to return more trace records.</p>
          <button className="investigation-toolbar__clear" type="button" onClick={onClearFilters}>
            Clear Filters
          </button>
        </div>
      ) : (
        <TraceTable
          traces={traces}
          isLoading={isLoading}
          error={error}
          onTraceSelect={onTraceSelect}
          selectedTraceKey={selectedTraceKey}
          highlightedTraceKeys={highlightedKeySet}
          emptyTitle={emptyTitle}
          emptyMessage={emptyMessage}
        />
      )}
    </section>
  )
}

export default TracePanel
