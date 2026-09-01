function getStatusColor(statusCode) {
  const code = (statusCode || '').toUpperCase()
  if (code === 'ERROR' || code === '5XX' || code === '500') return '#EF4444'
  if (code === 'WARN' || code === 'UNSET' || code === '4XX' || code === '400') return '#F59E0B'
  return '#10B981'
}

function InvestigationTimeline({ spans = [], traceSummary = {}, onSelectSpan }) {
  if (!Array.isArray(spans) || spans.length === 0) {
    return (
      <div style={{ padding: '2rem', textAlign: 'center', color: '#94A3B8' }}>
        No events available to render narrative timeline.
      </div>
    )
  }

  // Determine min start time
  let minStart = Number.MAX_SAFE_INTEGER
  spans.forEach((s) => {
    const t = new Date(s.startTime).getTime()
    if (!Number.isNaN(t) && t < minStart) minStart = t
  })
  if (minStart === Number.MAX_SAFE_INTEGER) minStart = 0

  // Sort spans chronologically by start time
  const timelineEvents = spans.map((span) => {
    const startTime = new Date(span.startTime).getTime()
    const offsetMs = !Number.isNaN(startTime) ? Math.max(0, startTime - minStart) : 0
    const durationMs = span.durationMs || 0

    return {
      ...span,
      offsetMs,
      durationMs,
    }
  })

  timelineEvents.sort((a, b) => a.offsetMs - b.offsetMs)

  return (
    <div
      className="investigation-timeline-panel"
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
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <div>
          <span style={{ fontSize: '0.7rem', color: '#94A3B8', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
            Investigation Narrative
          </span>
          <h3 style={{ margin: 0, fontSize: '1.05rem', color: '#F5F7FA' }}>Chronological Request Story</h3>
        </div>
        <span style={{ fontSize: '0.75rem', color: '#38bdf8', fontFamily: 'monospace' }}>
          {timelineEvents.length} Sequential Events
        </span>
      </div>

      {/* Narrative Event List */}
      <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem', position: 'relative' }}>
        {/* Connecting Vertical Guide Line */}
        <div
          style={{
            position: 'absolute',
            left: '52px',
            top: '12px',
            bottom: '12px',
            width: '2px',
            background: 'rgba(56, 189, 248, 0.2)',
            zIndex: 1,
          }}
        />

        {timelineEvents.map((evt, idx) => {
          const statusColor = getStatusColor(evt.statusCode)

          return (
            <div
              key={evt.spanId || idx}
              onClick={() => onSelectSpan?.(evt)}
              style={{
                display: 'grid',
                gridTemplateColumns: '60px 24px 1fr',
                alignItems: 'center',
                gap: '0.75rem',
                zIndex: 2,
                cursor: 'pointer',
                padding: '0.5rem',
                borderRadius: '6px',
                background: 'rgba(10, 25, 47, 0.6)',
                border: '1px solid rgba(255, 255, 255, 0.05)',
                transition: 'background 0.15s ease',
              }}
            >
              {/* Millisecond Offset Badge */}
              <div
                style={{
                  fontSize: '0.75rem',
                  fontWeight: 700,
                  color: '#38bdf8',
                  fontFamily: 'monospace',
                  textAlign: 'right',
                }}
              >
                +{evt.offsetMs} ms
              </div>

              {/* Node Dot */}
              <div
                style={{
                  width: '12px',
                  height: '12px',
                  borderRadius: '50%',
                  backgroundColor: statusColor,
                  border: '2px solid #07131F',
                  boxShadow: `0 0 6px ${statusColor}`,
                  margin: '0 auto',
                }}
              />

              {/* Narrative Content */}
              <div style={{ display: 'flex', flexDirection: 'column', gap: '0.2rem' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                  <span
                    style={{
                      fontSize: '0.7rem',
                      fontWeight: 600,
                      color: '#D4AF37',
                      background: 'rgba(212, 175, 55, 0.15)',
                      border: '1px solid rgba(212, 175, 55, 0.3)',
                      padding: '1px 6px',
                      borderRadius: '3px',
                    }}
                  >
                    {evt.serviceName || traceSummary?.serviceName || 'AtlasBank'}
                  </span>
                  <strong style={{ fontSize: '0.85rem', color: '#F5F7FA' }}>{evt.name}</strong>
                  <span style={{ fontSize: '0.75rem', color: '#94A3B8', fontFamily: 'monospace', marginLeft: 'auto' }}>
                    ({evt.durationMs} ms)
                  </span>
                </div>
                <span style={{ fontSize: '0.75rem', color: '#94A3B8' }}>
                  Kind: <code>{evt.kind || 'INTERNAL'}</code> · Status: <span style={{ color: statusColor }}>{evt.statusCode || 'OK'}</span>
                </span>
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}

export default InvestigationTimeline
