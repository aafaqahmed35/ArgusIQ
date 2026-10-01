import { Link } from 'react-router-dom'
import { buildTraceSearchHref, formatCount, formatDuration, formatPercent, LATENCY_BUCKET_ORDER } from '../../pages/overviewData'
import { EvidenceMetrics, EvidencePanel, EvidenceState, OperationEvidence } from './EvidencePrimitives'

function Distribution({ title, source, order = [], filter }) {
  const entries = Object.entries(source ?? {}).sort(([left], [right]) => order.length ? order.indexOf(left) - order.indexOf(right) : left.localeCompare(right))
  const complete = entries.every(([, count]) => typeof count === 'number' && Number.isFinite(count) && count >= 0)
  const total = complete ? entries.reduce((sum, [, count]) => sum + count, 0) : null
  return <EvidencePanel title={title} note="Persisted aggregate distribution · not a time series">
    {!source ? <p className="evidence-state">Distribution unavailable.</p> : !entries.length || total === 0 ? <p className="evidence-state">No observations represented.</p> : <><p className="evidence-note">{formatCount(total)} represented observations{!complete ? ' · incomplete counts; shares unavailable' : ''}</p><ul className="evidence-distribution">{entries.map(([label, count]) => {
      const share = total > 0 && typeof count === 'number' ? count / total * 100 : null
      return <li key={label}><div><strong>{label}</strong><span>{formatCount(count)} · {formatPercent(share)}</span>{filter && label !== 'UNKNOWN' ? <Link to={buildTraceSearchHref({ [filter]: label })} aria-label={`Investigate ${title}: ${label}`}>Investigate traces</Link> : null}</div><span className="evidence-bar" aria-hidden="true"><span style={{ width: `${share ?? 0}%` }} /></span></li>
    })}</ul></>}
  </EvidencePanel>
}

function Ranking({ title, endpoints }) {
  return <EvidencePanel title={title} note="Backend ranking · up to 10 endpoints · all persisted traces"><EvidenceState empty={!Array.isArray(endpoints) ? 'Ranking unavailable.' : !endpoints.length ? 'No endpoint observations.' : null}><div className="evidence-scroll" tabIndex={0} role="region" aria-label={title}><table className="evidence-table"><thead><tr><th scope="col">Endpoint</th><th scope="col">Traces</th><th scope="col">Errors / rate</th><th scope="col">Average / P95</th><th scope="col">Min / max</th><th scope="col">Investigation</th></tr></thead><tbody>{endpoints?.map((endpoint, index) => <tr key={`${endpoint.endpoint}-${index}`}><td className="evidence-name">{endpoint.endpoint || 'Unknown endpoint'}</td><td>{formatCount(endpoint.requestCount)}</td><td>{formatCount(endpoint.errorCount)} / {formatPercent(endpoint.errorRate)}</td><td>{formatDuration(endpoint.averageLatencyMs)} / {formatDuration(endpoint.p95LatencyMs)}</td><td>{formatDuration(endpoint.minimumLatencyMs)} / {formatDuration(endpoint.maximumLatencyMs)}</td><td>{endpoint.endpoint ? <Link to={buildTraceSearchHref({ endpoint: endpoint.endpoint })} aria-label={`Investigate traces containing endpoint ${endpoint.endpoint}`}>Investigate traces</Link> : '—'}</td></tr>)}</tbody></table></div></EvidenceState></EvidencePanel>
}

function AnalyticsEvidence({ metrics, isLoading, error }) {
  return <EvidenceState loading={isLoading} error={error || (!isLoading && !metrics)} empty={metrics?.totalTraces === 0 ? 'No telemetry yet. Analytics will appear after traces are persisted.' : null}>
    <EvidenceMetrics items={[
      ['Persisted traces', formatCount(metrics?.totalTraces)], ['Traces · last minute', formatCount(metrics?.requestsPerMinute)],
      ['Errors', formatCount(metrics?.errorCount)], ['Error rate', formatPercent(metrics?.errorRate)],
      ['Service identities', formatCount(metrics?.uniqueServices)], ['Endpoints', formatCount(metrics?.uniqueEndpoints)],
    ]} />
    <p className="evidence-note">Errors count trace status ERROR. Rates use all persisted traces; last-minute count uses a rolling UTC window. Missing values are shown as —.</p>
    <EvidencePanel title="Latency profile" note="Persisted trace durations · percentiles are not historical trends"><EvidenceMetrics items={[
      ['Average', formatDuration(metrics?.averageLatencyMs)], ['P50', formatDuration(metrics?.p50LatencyMs)], ['P95', formatDuration(metrics?.p95LatencyMs)],
      ['P99', formatDuration(metrics?.p99LatencyMs)], ['Minimum', formatDuration(metrics?.minimumLatencyMs)], ['Maximum', formatDuration(metrics?.maximumLatencyMs)],
    ]} /></EvidencePanel>
    <div className="evidence-composition"><Distribution title="Duration distribution" source={metrics?.traceDurationHistogram} order={LATENCY_BUCKET_ORDER} /><Distribution title="HTTP methods" source={metrics?.httpMethodDistribution} filter="httpMethod" /><Distribution title="Trace status" source={metrics?.statusCodeDistribution} filter="status" /></div>
    <p className="evidence-note">Trace status is the stored telemetry status, not an HTTP response-code histogram. Endpoint links use path-contains matching and may include other matching paths.</p>
    <div className="evidence-rankings"><Ranking title="Most failing endpoints" endpoints={metrics?.mostFailingEndpoints} /><Ranking title="Slowest endpoints · average duration" endpoints={metrics?.slowestEndpoints} /><Ranking title="Busiest endpoints" endpoints={metrics?.topEndpointsByTraffic} /><Ranking title="Fastest endpoints · average duration" endpoints={metrics?.fastestEndpoints} /></div>
    <EvidencePanel title="Slowest operations" note="Backend ranking by average span duration · up to 10 service/operation groups"><OperationEvidence operations={metrics?.slowestOperations} /></EvidencePanel>
  </EvidenceState>
}

export default AnalyticsEvidence
