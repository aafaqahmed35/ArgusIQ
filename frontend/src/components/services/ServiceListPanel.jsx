import { Link } from 'react-router-dom'
import { buildTraceSearchHref, formatCount, formatDuration, formatPercent } from '../../pages/overviewData'
import { observationLabel } from '../../pages/serviceEvidence'
import { EvidencePanel, EvidenceState } from '../analytics/EvidencePrimitives'

function ServiceListPanel({ services, isLoading, error, selectedServiceId, onServiceSelect, sortField, onSortFieldChange }) {
  return <EvidencePanel title="Observed services" note="Request metrics count persisted SERVER spans, not unique traces. Status is observational, not service health.">
    <div className="evidence-actions"><label>Order by <select value={sortField} onChange={(event) => onSortFieldChange(event.target.value)}><option value="traffic">Server requests</option><option value="latency">Average latency</option></select></label>{!isLoading && !error ? <span>{formatCount(services.length)} service identities</span> : null}</div>
    <EvidenceState loading={isLoading} error={error} empty={!services.length ? 'No services observed. Service identities appear after telemetry is ingested.' : null}>
      <div className="evidence-scroll" tabIndex={0} role="region" aria-label="Service inventory"><table className="evidence-table"><thead><tr><th scope="col">Service</th><th scope="col">Observation</th><th scope="col">Server requests</th><th scope="col">Errors / rate</th><th scope="col">Average / P95 / P99</th><th scope="col">Operations</th><th scope="col">Investigation</th></tr></thead><tbody>{services.map((service) => <tr key={service.id}><th scope="row" className="evidence-name"><button type="button" aria-pressed={selectedServiceId === service.id} onClick={() => onServiceSelect(service)} aria-label={`Inspect service ${service.serviceName}`}>{service.serviceName || 'Unknown service'}</button></th><td>{observationLabel(service.telemetryStatus)}</td><td>{formatCount(service.requestCount)}</td><td>{formatCount(service.errorCount)} / {formatPercent(service.errorRate)}</td><td>{formatDuration(service.averageLatencyMs)} / {formatDuration(service.p95LatencyMs)} / {formatDuration(service.p99LatencyMs)}</td><td>{formatCount(service.observedOperationCount)}</td><td>{service.serviceName ? <Link to={buildTraceSearchHref({ service: service.serviceName })} aria-label={`Investigate entry-service traces for ${service.serviceName}`}>Entry-service traces</Link> : '—'}</td></tr>)}</tbody></table></div>
    </EvidenceState>
    <p className="evidence-note">Recently observed means last seen within five minutes. Recent errors means at least 10% ERROR server spans in that window. Explorer service filters match trace entry identity, not every participating service.</p>
  </EvidencePanel>
}

export default ServiceListPanel
