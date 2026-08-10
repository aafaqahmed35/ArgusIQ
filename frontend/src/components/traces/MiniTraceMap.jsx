import React, { useMemo } from 'react'

function MiniTraceMap({ spans = [], traceSummary = {} }) {
  const topology = useMemo(() => {
    if (!Array.isArray(spans) || spans.length === 0) {
      const rootService = traceSummary?.serviceName || 'AtlasBank'
      return {
        services: [rootService],
        edges: [],
      }
    }

    const services = new Set()
    const edgesMap = new Map()

    spans.forEach((span) => {
      const sName = span.serviceName || traceSummary?.serviceName || 'AtlasBank'
      services.add(sName)
    })

    // Map parent service to child service
    const spanMap = new Map()
    spans.forEach((s) => spanMap.set(s.spanId, s))

    spans.forEach((span) => {
      if (span.parentSpanId && spanMap.has(span.parentSpanId)) {
        const parent = spanMap.get(span.parentSpanId)
        const parentService = parent.serviceName || traceSummary?.serviceName || 'AtlasBank'
        const childService = span.serviceName || traceSummary?.serviceName || 'AtlasBank'

        if (parentService !== childService) {
          const edgeKey = `${parentService}➔${childService}`
          edgesMap.set(edgeKey, (edgesMap.get(edgeKey) || 0) + 1)
        }
      }
    })

    return {
      services: Array.from(services),
      edges: Array.from(edgesMap.entries()).map(([key, count]) => {
        const [from, to] = key.split('➔')
        return { from, to, count }
      }),
    }
  }, [spans, traceSummary])

  return (
    <div
      className="mini-trace-map-panel"
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
          Topology Graph
        </span>
        <h3 style={{ margin: 0, fontSize: '1.05rem', color: '#F5F7FA' }}>Service Communication Flow</h3>
      </div>

      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          flexWrap: 'wrap',
          gap: '1.5rem',
          padding: '1.5rem',
          background: '#0A192F',
          borderRadius: '8px',
          border: '1px solid rgba(255, 255, 255, 0.05)',
          minHeight: '140px',
        }}
      >
        {topology.services.map((service, idx) => (
          <React.Fragment key={service}>
            <div
              style={{
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                gap: '0.35rem',
                background: 'rgba(15, 23, 42, 0.9)',
                border: '1px solid #38bdf8',
                boxShadow: '0 0 12px rgba(56, 189, 248, 0.15)',
                borderRadius: '8px',
                padding: '0.75rem 1.25rem',
              }}
            >
              <span style={{ fontSize: '1.2rem' }}>🖥️</span>
              <strong style={{ fontSize: '0.85rem', color: '#F5F7FA' }}>{service}</strong>
              <span style={{ fontSize: '0.7rem', color: '#94A3B8' }}>Active Node</span>
            </div>

            {idx < topology.services.length - 1 && (
              <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', color: '#D4AF37' }}>
                <span style={{ fontSize: '0.75rem', fontWeight: 600 }}>calls</span>
                <span style={{ fontSize: '1.4rem' }}>➔</span>
              </div>
            )}
          </React.Fragment>
        ))}
      </div>
    </div>
  )
}

export default MiniTraceMap
