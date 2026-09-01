import { useMemo } from 'react'
import { calculateServiceStats } from '../../lib/traceMetrics'

function ServiceCallStats({ spans = [], totalTraceDurationMs = 1 }) {
  const serviceStats = useMemo(
    () => calculateServiceStats(spans, totalTraceDurationMs),
    [spans, totalTraceDurationMs]
  )

  if (serviceStats.length === 0) {
    return (
      <div style={{ padding: '2rem', textAlign: 'center', color: '#94A3B8' }}>
        No service telemetry available to compute statistics.
      </div>
    )
  }

  return (
    <div
      className="service-call-stats-panel"
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: '1rem',
        background: '#07131F',
        border: '1px solid rgba(255, 255, 255, 0.08)',
        borderRadius: '8px',
        padding: '1.25rem',
      }}
    >
      <div>
        <span style={{ fontSize: '0.7rem', color: '#94A3B8', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
          Service Statistics
        </span>
        <h3 style={{ margin: 0, fontSize: '1.05rem', color: '#F5F7FA' }}>Per-Service Latency & Contribution Breakdown</h3>
      </div>

      <div style={{ overflowX: 'auto' }}>
        <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.8rem' }}>
          <thead>
            <tr style={{ background: '#0A192F', borderBottom: '1px solid rgba(255, 255, 255, 0.1)', textAlign: 'left', color: '#94A3B8' }}>
              <th style={{ padding: '0.5rem 0.75rem' }}>Rank</th>
              <th style={{ padding: '0.5rem 0.75rem' }}>Service Name</th>
              <th style={{ padding: '0.5rem 0.75rem' }}>Calls</th>
              <th style={{ padding: '0.5rem 0.75rem' }}>Avg Latency</th>
              <th style={{ padding: '0.5rem 0.75rem' }}>Max Latency</th>
              <th style={{ padding: '0.5rem 0.75rem' }}>Errors</th>
              <th style={{ padding: '0.5rem 0.75rem' }}>Trace Contribution</th>
            </tr>
          </thead>
          <tbody>
            {serviceStats.map((item) => (
              <tr
                key={item.serviceName}
                style={{
                  borderBottom: '1px solid rgba(255, 255, 255, 0.04)',
                  background: item.isSlowest ? 'rgba(212, 175, 55, 0.05)' : 'transparent',
                }}
              >
                <td style={{ padding: '0.5rem 0.75rem', fontWeight: 700, color: item.isSlowest ? '#D4AF37' : '#94A3B8' }}>
                  #{item.rank}
                </td>
                <td style={{ padding: '0.5rem 0.75rem', fontWeight: 600, color: '#F5F7FA' }}>
                  {item.serviceName} {item.isSlowest && <span style={{ color: '#D4AF37', fontSize: '0.7rem' }}>(Slowest)</span>}
                </td>
                <td style={{ padding: '0.5rem 0.75rem', color: '#38bdf8' }}>{item.requestCount}</td>
                <td style={{ padding: '0.5rem 0.75rem', fontFamily: 'monospace', color: '#F5F7FA' }}>{item.avgDurationMs} ms</td>
                <td style={{ padding: '0.5rem 0.75rem', fontFamily: 'monospace', color: item.isSlowest ? '#D4AF37' : '#F5F7FA' }}>
                  {item.maxDurationMs} ms
                </td>
                <td style={{ padding: '0.5rem 0.75rem', color: item.errorCount > 0 ? '#EF4444' : '#10B981', fontWeight: 600 }}>
                  {item.errorCount}
                </td>
                <td style={{ padding: '0.5rem 0.75rem', width: '160px' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                    <div style={{ flexGrow: 1, height: '6px', background: 'rgba(255,255,255,0.1)', borderRadius: '3px', overflow: 'hidden' }}>
                      <div
                        style={{
                          width: `${item.contributionPct}%`,
                          height: '100%',
                          background: item.isSlowest ? '#D4AF37' : '#38bdf8',
                          borderRadius: '3px',
                        }}
                      />
                    </div>
                    <span style={{ fontSize: '0.75rem', color: item.isSlowest ? '#D4AF37' : '#94A3B8', fontFamily: 'monospace' }}>
                      {item.contributionPct}%
                    </span>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}

export default ServiceCallStats
