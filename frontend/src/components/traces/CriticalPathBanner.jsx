import React from 'react'

function CriticalPathBanner({ criticalPathInfo }) {
  if (!criticalPathInfo || !criticalPathInfo.criticalPathNodes || criticalPathInfo.criticalPathNodes.length === 0) {
    return null
  }

  const {
    totalCriticalPathMs,
    criticalPathPercentage,
    slowestSpan,
    slowestService,
    criticalPathNodes,
  } = criticalPathInfo

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
      {/* Title */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
          <span style={{ fontSize: '1.2rem', color: '#D4AF37' }}>⚡</span>
          <strong style={{ fontSize: '0.95rem', color: '#D4AF37', letterSpacing: '0.02em' }}>
            Critical Path & Bottleneck Analysis
          </strong>
        </div>
        <span
          style={{
            fontSize: '0.75rem',
            fontWeight: 700,
            color: '#D4AF37',
            background: 'rgba(212, 175, 55, 0.2)',
            border: '1px solid #D4AF37',
            padding: '2px 8px',
            borderRadius: '12px',
          }}
        >
          {criticalPathPercentage}% of Total Trace Time
        </span>
      </div>

      {/* Metrics Row */}
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))',
          gap: '0.75rem',
        }}
      >
        <div
          style={{
            background: 'rgba(7, 19, 31, 0.8)',
            border: '1px solid rgba(212, 175, 55, 0.2)',
            borderRadius: '6px',
            padding: '0.5rem 0.75rem',
          }}
        >
          <div style={{ fontSize: '0.7rem', color: '#94A3B8', textTransform: 'uppercase' }}>Critical Path Time</div>
          <strong style={{ fontSize: '1.1rem', color: '#F5F7FA', fontFamily: 'monospace' }}>
            {totalCriticalPathMs} ms
          </strong>
        </div>

        <div
          style={{
            background: 'rgba(7, 19, 31, 0.8)',
            border: '1px solid rgba(212, 175, 55, 0.2)',
            borderRadius: '6px',
            padding: '0.5rem 0.75rem',
          }}
        >
          <div style={{ fontSize: '0.7rem', color: '#94A3B8', textTransform: 'uppercase' }}>Slowest Service</div>
          <strong style={{ fontSize: '1.0rem', color: '#D4AF37' }}>{slowestService}</strong>
        </div>

        <div
          style={{
            background: 'rgba(7, 19, 31, 0.8)',
            border: '1px solid rgba(212, 175, 55, 0.2)',
            borderRadius: '6px',
            padding: '0.5rem 0.75rem',
          }}
        >
          <div style={{ fontSize: '0.7rem', color: '#94A3B8', textTransform: 'uppercase' }}>Slowest Operation</div>
          <strong style={{ fontSize: '0.9rem', color: '#F5F7FA' }} title={slowestSpan?.name}>
            {slowestSpan ? slowestSpan.name : 'N/A'} ({slowestSpan ? slowestSpan.durationMs : 0} ms)
          </strong>
        </div>
      </div>

      {/* Critical Path Execution Chain */}
      <div style={{ display: 'flex', flexDirection: 'column', gap: '0.25rem' }}>
        <div style={{ fontSize: '0.75rem', fontWeight: 600, color: '#94A3B8' }}>Execution Chain:</div>
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            flexWrap: 'wrap',
            gap: '0.35rem',
            fontSize: '0.75rem',
          }}
        >
          {criticalPathNodes.map((node, i) => (
            <React.Fragment key={node.spanId || i}>
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
                <strong style={{ color: '#D4AF37' }}>{node.serviceName}</strong>: {node.name} ({node.durationMs} ms)
              </span>
              {i < criticalPathNodes.length - 1 && <span style={{ color: '#D4AF37', fontWeight: 'bold' }}>➔</span>}
            </React.Fragment>
          ))}
        </div>
      </div>
    </div>
  )
}

export default CriticalPathBanner
