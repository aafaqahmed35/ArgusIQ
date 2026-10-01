import { Link } from 'react-router-dom'
import { buildTraceSearchHref, formatCount, formatDuration, formatPercent } from '../../pages/overviewData'
import { observationLabel, observationTime } from '../../pages/serviceEvidence'
import { EvidenceMetrics, EvidencePanel, EvidenceState, OperationEvidence } from '../analytics/EvidencePrimitives'

function RecentEvidence({ title, traces }) {
  return <EvidencePanel title={title} note="Up to 10 returned traces · participating service evidence"><EvidenceState empty={!Array.isArray(traces) ? 'Recent evidence unavailable.' : !traces.length ? 'No matching traces in the returned evidence.' : null}><div className="evidence-scroll" tabIndex={0} role="region" aria-label={title}><table className="evidence-table"><thead><tr><th scope="col">Trace / root operation</th><th scope="col">Trace status</th><th scope="col">Duration</th><th scope="col">Start (UTC)</th><th scope="col">Investigation</th></tr></thead><tbody>{traces?.map((trace, index) => <tr key={trace.traceId || index}><td className="evidence-name"><strong>{trace.rootSpanName || 'Unknown operation'}</strong><code>{trace.traceId || 'Trace ID unavailable'}</code></td><td>{trace.statusCode || 'UNKNOWN'}</td><td>{formatDuration(trace.durationMs)}</td><td>{observationTime(trace.startTime)}</td><td>{trace.traceId ? <Link to={buildTraceSearchHref({ traceId: trace.traceId })} aria-label={`Inspect trace ${trace.traceId}`}>Inspect trace</Link> : '—'}</td></tr>)}</tbody></table></div></EvidenceState></EvidencePanel>
}

function ServiceDetailPanel({ service, isLoading, error, relationships, relationshipError, relationshipLoading }) {
  const edges = relationships?.edges?.filter((edge) => edge.source === service?.serviceName || edge.target === service?.serviceName)
  return <EvidenceState loading={isLoading} error={error} empty={!service ? 'Select a service to inspect operations and recent evidence.' : null}>
    {service ? <div className="service-evidence-detail">
      <EvidencePanel title={service.serviceName || 'Unknown service'} note="Service investigation · persisted SERVER-span aggregates">
        {service.serviceName ? <div className="evidence-actions"><Link to={buildTraceSearchHref({ service: service.serviceName })}>Investigate entry-service traces</Link></div> : null}
        <EvidenceMetrics items={[
          ['Observation', observationLabel(service.telemetryStatus)], ['Server requests', formatCount(service.requestCount)], ['Last minute · UTC', formatCount(service.requestsPerMinute)],
          ['Errors / rate', `${formatCount(service.errorCount)} / ${formatPercent(service.errorRate)}`], ['Average', formatDuration(service.averageLatencyMs)], ['P95 / P99', `${formatDuration(service.p95LatencyMs)} / ${formatDuration(service.p99LatencyMs)}`],
          ['Min / max', `${formatDuration(service.minimumLatencyMs)} / ${formatDuration(service.maximumLatencyMs)}`], ['Operations', formatCount(service.observedOperationCount)], ['Outgoing structural links', formatCount(service.dependencyCount)],
          ['First observed', observationTime(service.firstSeen)], ['Last observed', observationTime(service.lastSeen)],
          ['Environment', service.environment || 'Not observed'], ['Version', service.version || 'Not observed'], ['Language', service.language || 'Not observed'],
        ]} />
      </EvidencePanel>
      <EvidencePanel title="Operations by span count" note="Backend ordering · up to 10 operations · all span kinds, not only SERVER"><OperationEvidence operations={service.topOperationsByTraffic} /></EvidencePanel>
      <div className="evidence-rankings"><EvidencePanel title="Slowest operation" note="Backend selection by average span duration"><OperationEvidence operations={service.slowestOperation === undefined ? undefined : service.slowestOperation ? [service.slowestOperation] : []} /></EvidencePanel><EvidencePanel title="Fastest operation" note="Backend selection by average span duration"><OperationEvidence operations={service.fastestOperation === undefined ? undefined : service.fastestOperation ? [service.fastestOperation] : []} /></EvidencePanel></div>
      <EvidencePanel title="Observed service relationships" note="Persisted parent → child span links within the same trace. These links do not establish causation or synchronous calls."><EvidenceState loading={relationshipLoading} error={relationshipError} empty={!Array.isArray(edges) ? 'Relationship evidence unavailable.' : !edges.length ? 'No cross-service parent-child links returned for this service.' : null}><ul className="evidence-edges">{edges?.map((edge, index) => <li key={`${edge.source}-${edge.target}-${index}`}><span>Parent: <strong>{edge.source}</strong></span><span>Child: <strong>{edge.target}</strong></span></li>)}</ul></EvidenceState></EvidencePanel>
      <RecentEvidence title="Recent traces" traces={service.recentTraces} /><RecentEvidence title="Recent error evidence" traces={service.recentErrors} />
      <p className="evidence-note">Recent error evidence can include an ERROR span from this service even when the trace-level status is not ERROR. No errors in a returned sample is not a health or availability guarantee.</p>
    </div> : null}
  </EvidenceState>
}

export default ServiceDetailPanel
