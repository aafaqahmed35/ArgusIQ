import TraceTable from '../TraceTable'

function TracePanel({ traces, isLoading, error }) {
  return (
    <section className="trace-panel" aria-label="Trace records">
      <div className="trace-panel__header">
        <div>
          <p className="section-kicker">Request stream</p>
          <h2>Recent Traces</h2>
        </div>
        <span className="trace-panel__count">{traces.length.toLocaleString()} records</span>
      </div>

      <TraceTable traces={traces} isLoading={isLoading} error={error} />
    </section>
  )
}

export default TracePanel
