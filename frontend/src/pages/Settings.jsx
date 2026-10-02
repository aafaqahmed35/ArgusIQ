import { Link } from 'react-router-dom'
import PageHeader from '../components/layout/PageHeader'
import { EvidencePanel } from '../components/analytics/EvidencePrimitives'
import { API_BASE_URL, WEBSOCKET_URL, endpointLabel } from '../config/runtimeConfig'
import '../styles/dashboard.css'
import '../styles/evidence.css'

export default function Settings() {
  return <div className="product-information evidence-workspace">
    <section className="product-information__header"><PageHeader eyebrow="Product configuration" title="Settings" subtitle="Read-only client configuration and product information." showConnectionStatus={false} statusNote="Read-only" /></section>
    <div className="product-information__grid">
      <EvidencePanel title="Client connections" note="Build-time configuration · endpoint addresses do not confirm reachability">
        <dl className="product-information__properties">
          <div><dt>REST API base</dt><dd><code>{endpointLabel(API_BASE_URL)}</code></dd></div>
          <div><dt>Live trace transport</dt><dd><code>{endpointLabel(WEBSOCKET_URL)}</code></dd></div>
          <div><dt>Live subscription</dt><dd><code>/topic/traces</code></dd></div>
          <div><dt>Timestamp interpretation</dt><dd>Backend timestamps are interpreted as UTC. Investigation inputs and evidence use explicit time labels.</dd></div>
        </dl>
        <p className="evidence-note">API and transport addresses are supplied by VITE_ARGUSIQ_API_BASE_URL and VITE_ARGUSIQ_WEBSOCKET_URL, with local defaults. Changes require a frontend build or development server restart. Credentials and URL parameters are omitted from this display.</p>
        <div className="evidence-actions"><Link to="/infrastructure">Review telemetry boundary and connectivity</Link></div>
      </EvidencePanel>
      <EvidencePanel title="ArgusIQ" note="Investigation-centric observability for backend systems">
        <ul className="product-information__list">
          <li><strong>Observe and correlate</strong><span>Persisted application evidence, service relationships, and deterministic alert rule matches.</span><Link to="/">Open Overview</Link></li>
          <li><strong>Investigate and explain</strong><span>Server-side trace search, structural Critical Path, and deterministic evidence-backed Explain.</span><Link to="/traces">Open Trace Explorer</Link></li>
          <li><strong>Configuration boundary</strong><span>No editable client preferences are available. Account identity, organizations, roles, credential management, notifications, retention, billing, and team settings are not provided.</span></li>
        </ul>
        <p className="evidence-note">Recommendation is not implemented. Critical Path and Explain describe observed structure and evidence; they do not establish causal root cause.</p>
      </EvidencePanel>
    </div>
  </div>
}
