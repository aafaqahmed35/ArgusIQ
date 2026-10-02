import { Link } from 'react-router-dom'
import PageHeader from '../components/layout/PageHeader'
import { EvidenceMetrics, EvidencePanel, EvidenceState } from '../components/analytics/EvidencePrimitives'
import { useTraces } from '../hooks/useTraces'
import '../styles/dashboard.css'
import '../styles/evidence.css'

export function InfrastructureEvidence({ isLoading, error, recentTraces, recentTraceLimit, websocketStatus, onRefresh }) {
  return <div className="product-information evidence-workspace">
    <section className="product-information__header"><PageHeader eyebrow="Telemetry boundary" title="Infrastructure Evidence" subtitle="Application evidence and the limits of what ArgusIQ currently observes." websocketStatus={websocketStatus} isLoading={isLoading} onRefresh={onRefresh} /></section>
    <EvidencePanel title="Trace connectivity" note="Shared recent trace window · independent of the live connection">
      <EvidenceState loading={isLoading} error={error} empty={!recentTraces.length ? 'No recent traces returned. Ingestion activity and infrastructure condition cannot be inferred.' : null}>
        <EvidenceMetrics items={[
          ['Recent trace records', recentTraces.length.toLocaleString()],
          ['Window limit', recentTraceLimit.toLocaleString()],
          ['Trace search', 'Response received'],
        ]} />
      </EvidenceState>
      <p className="evidence-note">Live connection status describes trace updates only. REST evidence remains independent. A successful trace response does not establish continuous ingestion, host health, or availability.</p>
    </EvidencePanel>
    <div className="product-information__grid">
      <EvidencePanel title="What ArgusIQ observes" note="Supported application telemetry · values appear only where observed">
        <ul className="product-information__list">
          <li><strong>Application traces</strong><span>Trace identities, spans, timing, stored status, and request metadata.</span><Link to="/traces">Inspect traces</Link></li>
          <li><strong>Service identities and runtime metadata</strong><span>Observed environment, service version, and language. Missing attributes remain unobserved.</span><Link to="/services">Explore services</Link></li>
          <li><strong>Request, error, and latency evidence</strong><span>Persisted trace aggregates and SERVER-span service metrics, with explicit sampling boundaries.</span><Link to="/analytics">Inspect analytics</Link></li>
          <li><strong>Structural service relationships</strong><span>Validated parent → child span links within a trace. These do not prove causation or network health.</span><Link to="/services">Inspect relationships</Link></li>
        </ul>
      </EvidencePanel>
      <EvidencePanel title="Infrastructure evidence not ingested" note="Unsupported evidence · no inferred health values">
        <ul className="product-information__list product-information__list--boundary">
          <li><strong>Hosts and containers</strong><span>Host CPU, host memory, disk, storage utilization, and container metrics.</span></li>
          <li><strong>Data and messaging systems</strong><span>Database, Redis/cache, Kafka/queue health and infrastructure metrics.</span></li>
          <li><strong>Network and availability</strong><span>Network infrastructure metrics, uptime, and availability.</span></li>
        </ul>
        <p className="evidence-note">OpenTelemetry trace ingestion provides application evidence. Application spans involving a database, cache, or queue do not establish the condition of that infrastructure.</p>
      </EvidencePanel>
    </div>
  </div>
}

export default function Infrastructure() {
  const { isLoading, error, recentTraces, recentTraceLimit, websocketStatus, refreshRecentTraces } = useTraces()
  return <InfrastructureEvidence {...{ isLoading, error, recentTraces, recentTraceLimit, websocketStatus }} onRefresh={refreshRecentTraces} />
}
