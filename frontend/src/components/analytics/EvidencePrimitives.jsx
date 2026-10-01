import { Link } from 'react-router-dom'
import { buildTraceSearchHref, formatCount, formatDuration } from '../../pages/overviewData'

export function EvidencePanel({ title, note, children }) {
  return <section className="evidence-panel"><header><h2>{title}</h2>{note ? <p>{note}</p> : null}</header>{children}</section>
}

export function EvidenceState({ loading, error, empty, children }) {
  if (loading) return <p className="evidence-state" role="status" aria-busy="true">Loading persisted evidence…</p>
  if (error) return <p className="evidence-state" role="alert">Backend evidence unavailable. Use Refresh to retry.</p>
  if (empty) return <p className="evidence-state">{empty}</p>
  return children
}

export function EvidenceMetrics({ items }) {
  return <dl className="evidence-metrics">{items.map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value}</dd></div>)}</dl>
}

export function OperationEvidence({ operations }) {
  if (!Array.isArray(operations)) return <p className="evidence-state">Operation evidence unavailable.</p>
  if (!operations.length) return <p className="evidence-state">No operation spans observed.</p>
  return <div className="evidence-scroll" tabIndex={0} role="region" aria-label="Operation evidence"><table className="evidence-table"><thead><tr><th scope="col">Service / operation</th><th scope="col">Spans</th><th scope="col">Errors</th><th scope="col">Average</th><th scope="col">Min / max</th><th scope="col">Investigation</th></tr></thead><tbody>{operations.map((operation, index) => <tr key={`${operation.serviceName}-${operation.operationName}-${index}`}><td className="evidence-name"><small>{operation.serviceName || 'Unknown service'}</small><strong>{operation.operationName || 'Unknown operation'}</strong></td><td>{formatCount(operation.observationCount)}</td><td>{formatCount(operation.errorCount)}</td><td>{formatDuration(operation.averageLatencyMs)}</td><td>{formatDuration(operation.minimumLatencyMs)} / {formatDuration(operation.maximumLatencyMs)}</td><td>{operation.operationName ? <Link to={buildTraceSearchHref({ query: operation.operationName })} aria-label={`Search traces for operation ${operation.operationName}`}>Search trace text</Link> : '—'}</td></tr>)}</tbody></table><p className="evidence-note">Text search matches trace-level fields, not every child span. Results are not an exact operation cohort.</p></div>
}
