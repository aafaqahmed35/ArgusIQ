import React from 'react'

function readableIssue(issue) {
  return String(issue || '').toLowerCase().replaceAll('_', ' ')
}

function CriticalPathBanner({ criticalPathInfo }) {
  if (!criticalPathInfo?.status) return null

  const {
    status,
    issues,
    totalCriticalPathMs,
    criticalPathPercentage,
    largestContributor,
    criticalPathNodes,
  } = criticalPathInfo
  const isUnavailable = status === 'UNAVAILABLE'

  return (
    <div
      className="critical-path-banner"
      style={{
        background: 'linear-gradient(135deg, rgba(212, 175, 55, 0.12) 0%, rgba(10, 25, 47, 0.95) 100%)',
        border: '1px solid rgba(212, 175, 55, 0.4)',
        borderRadius: '8px',
        padding: '1rem',
        display: 'flex',
        flexDirection: 'column',
        gap: '0.75rem',
        boxShadow: '0 4px 20px rgba(212, 175, 55, 0.08)',
      }}
    >
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
          <span style={{ fontSize: '1.2rem', color: '#D4AF37' }}>⚡</span>
          <strong style={{ fontSize: '0.95rem', color: '#D4AF37', letterSpacing: '0.02em' }}>
            Causal Critical Path
          </strong>
        </div>
        <span
          style={{
            fontSize: '0.75rem',
            fontWeight: 700,
            color: status === 'COMPLETE' ? '#86EFAC' : '#FBBF24',
            background: 'rgba(212, 175, 55, 0.12)',
            border: '1px solid currentColor',
            padding: '2px 8px',
            borderRadius: '12px',
          }}
        >
          {status}
        </span>
      </div>

      {isUnavailable ? (
        <div style={{ color: '#FBBF24', fontSize: '0.82rem' }}>
          A trustworthy causal path cannot be derived from this trace graph.
        </div>
      ) : (
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))',
            gap: '0.75rem',
          }}
        >
          <Metric label="Critical Path Time" value={`${totalCriticalPathMs} ms`} />
          <Metric label="Wall-clock Coverage" value={`${criticalPathPercentage}%`} />
          <Metric
            label="Largest Contribution"
            value={largestContributor
              ? `${largestContributor.serviceName}: ${largestContributor.name} (${largestContributor.contributionDurationMs} ms)`
              : 'None'}
          />
        </div>
      )}

      {issues.length > 0 && (
        <div style={{ fontSize: '0.75rem', color: '#FBBF24' }}>
          Limitations: {issues.map(readableIssue).join(', ')}
        </div>
      )}

      {criticalPathNodes.length > 0 && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.25rem' }}>
          <div style={{ fontSize: '0.75rem', fontWeight: 600, color: '#94A3B8' }}>Contributing spans:</div>
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              flexWrap: 'wrap',
              gap: '0.35rem',
              fontSize: '0.75rem',
            }}
          >
            {criticalPathNodes.map((node, index) => (
              <React.Fragment key={node.spanId || index}>
                <span
                  style={{
                    background: 'rgba(212, 175, 55, 0.15)',
                    border: '1px solid rgba(212, 175, 55, 0.3)',
                    color: '#F5F7FA',
                    padding: '2px 8px',
                    borderRadius: '4px',
                    fontFamily: 'monospace',
                  }}
                >
                  <strong style={{ color: '#D4AF37' }}>{node.serviceName}</strong>: {node.name}
                  {' '}({node.contributionDurationMs} ms contribution)
                </span>
                {index < criticalPathNodes.length - 1 && (
                  <span style={{ color: '#D4AF37', fontWeight: 'bold' }}>➔</span>
                )}
              </React.Fragment>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}

function Metric({ label, value }) {
  return (
    <div
      style={{
        background: 'rgba(7, 19, 31, 0.8)',
        border: '1px solid rgba(212, 175, 55, 0.2)',
        borderRadius: '6px',
        padding: '0.5rem 0.75rem',
      }}
    >
      <div style={{ fontSize: '0.7rem', color: '#94A3B8', textTransform: 'uppercase' }}>{label}</div>
      <strong style={{ fontSize: '0.9rem', color: '#F5F7FA' }}>{value}</strong>
    </div>
  )
}

export default CriticalPathBanner
