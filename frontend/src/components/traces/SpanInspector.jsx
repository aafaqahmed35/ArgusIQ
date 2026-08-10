import React, { useState } from 'react'

function formatDate(value) {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  return new Intl.DateTimeFormat('en', { dateStyle: 'medium', timeStyle: 'medium' }).format(date)
}

function getStatusBadge(status) {
  const code = (status || '').toUpperCase()
  if (code === 'ERROR') return { label: 'ERROR', color: '#EF4444' }
  if (code === 'WARN' || code === 'UNSET') return { label: 'WARN', color: '#F59E0B' }
  return { label: 'OK', color: '#10B981' }
}

function SpanInspector({ selectedSpan, spanMap = new Map() }) {
  const [copiedKey, setCopiedKey] = useState(null)

  const handleCopy = (e, text, keyName) => {
    e.stopPropagation()
    if (!text) return
    navigator.clipboard.writeText(typeof text === 'object' ? JSON.stringify(text, null, 2) : String(text))
    setCopiedKey(keyName)
    setTimeout(() => setCopiedKey(null), 2000)
  }

  if (!selectedSpan) {
    return (
      <div
        style={{
          padding: '2rem',
          textAlign: 'center',
          background: 'rgba(10, 25, 47, 0.4)',
          border: '1px solid rgba(255, 255, 255, 0.08)',
          borderRadius: '8px',
          color: '#94A3B8',
        }}
      >
        <p style={{ margin: 0, fontWeight: 600, fontSize: '0.95rem' }}>Select a span to inspect details</p>
        <span style={{ fontSize: '0.8rem' }}>
          Click any node in the Trace Tree, Waterfall, or Timeline to inspect full timing, attributes, and relationships.
        </span>
      </div>
    )
  }

  const {
    spanId,
    traceId,
    parentSpanId,
    name,
    serviceName,
    kind,
    startTime,
    endTime,
    durationMs,
    statusCode,
    statusMessage,
    startOffsetMs,
  } = selectedSpan

  const statusBadge = getStatusBadge(statusCode)
  const parentSpan = parentSpanId ? spanMap.get(parentSpanId) : null
  const children = selectedSpan.children || []

  return (
    <div
      className="span-inspector-panel"
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: '1.25rem',
        background: '#0A192F',
        border: '1px solid rgba(255, 255, 255, 0.1)',
        borderRadius: '8px',
        padding: '1.25rem',
      }}
    >
      {/* Header */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', borderBottom: '1px solid rgba(255,255,255,0.08)', pb: '0.75rem' }}>
        <div>
          <span style={{ fontSize: '0.7rem', color: '#94A3B8', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
            Span Inspector
          </span>
          <h3 style={{ margin: 0, fontSize: '1.1rem', color: '#F5F7FA' }}>{name}</h3>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
          <span
            style={{
              fontSize: '0.75rem',
              fontWeight: 700,
              color: statusBadge.color,
              background: 'rgba(15, 23, 42, 0.8)',
              border: `1px solid ${statusBadge.color}`,
              padding: '2px 8px',
              borderRadius: '12px',
            }}
          >
            {statusBadge.label}
          </span>
          <button
            type="button"
            onClick={(e) => handleCopy(e, selectedSpan, 'json')}
            style={{
              background: 'rgba(255, 255, 255, 0.05)',
              border: '1px solid rgba(255, 255, 255, 0.1)',
              borderRadius: '4px',
              color: copiedKey === 'json' ? '#10B981' : '#F5F7FA',
              cursor: 'pointer',
              fontSize: '0.75rem',
              padding: '4px 8px',
            }}
          >
            {copiedKey === 'json' ? '✓ JSON Copied' : '📋 Copy JSON'}
          </button>
        </div>
      </div>

      {/* General Section */}
      <div>
        <h4 style={{ fontSize: '0.8rem', color: '#38bdf8', textTransform: 'uppercase', margin: '0 0 0.5rem 0' }}>General</h4>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '0.5rem', fontSize: '0.8rem' }}>
          <div>
            <span style={{ color: '#94A3B8' }}>Service: </span>
            <strong style={{ color: '#F5F7FA' }}>{serviceName}</strong>
          </div>
          <div>
            <span style={{ color: '#94A3B8' }}>Kind: </span>
            <code style={{ color: '#F5F7FA' }}>{kind}</code>
          </div>
          <div>
            <span style={{ color: '#94A3B8' }}>Span ID: </span>
            <code style={{ color: '#F5F7FA' }}>{spanId}</code>
            <button
              type="button"
              onClick={(e) => handleCopy(e, spanId, 'spanId')}
              style={{ background: 'none', border: 'none', color: copiedKey === 'spanId' ? '#10B981' : '#94A3B8', cursor: 'pointer', fontSize: '0.75rem', marginLeft: '4px' }}
            >
              {copiedKey === 'spanId' ? '✓' : '📋'}
            </button>
          </div>
          <div>
            <span style={{ color: '#94A3B8' }}>Trace ID: </span>
            <code style={{ color: '#F5F7FA' }}>{traceId}</code>
            <button
              type="button"
              onClick={(e) => handleCopy(e, traceId, 'traceId')}
              style={{ background: 'none', border: 'none', color: copiedKey === 'traceId' ? '#10B981' : '#94A3B8', cursor: 'pointer', fontSize: '0.75rem', marginLeft: '4px' }}
            >
              {copiedKey === 'traceId' ? '✓' : '📋'}
            </button>
          </div>
        </div>
      </div>

      {/* Timing Section */}
      <div>
        <h4 style={{ fontSize: '0.8rem', color: '#38bdf8', textTransform: 'uppercase', margin: '0 0 0.5rem 0' }}>Timing</h4>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '0.5rem', fontSize: '0.8rem' }}>
          <div>
            <span style={{ color: '#94A3B8' }}>Duration: </span>
            <strong style={{ color: '#D4AF37', fontFamily: 'monospace' }}>{durationMs} ms</strong>
          </div>
          <div>
            <span style={{ color: '#94A3B8' }}>Start Offset: </span>
            <span style={{ color: '#F5F7FA', fontFamily: 'monospace' }}>+{startOffsetMs} ms</span>
          </div>
          <div>
            <span style={{ color: '#94A3B8' }}>Start Time: </span>
            <span style={{ color: '#F5F7FA' }}>{formatDate(startTime)}</span>
          </div>
          <div>
            <span style={{ color: '#94A3B8' }}>End Time: </span>
            <span style={{ color: '#F5F7FA' }}>{formatDate(endTime)}</span>
          </div>
        </div>
      </div>

      {/* Relationships Section */}
      <div>
        <h4 style={{ fontSize: '0.8rem', color: '#38bdf8', textTransform: 'uppercase', margin: '0 0 0.5rem 0' }}>Relationships</h4>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.35rem', fontSize: '0.8rem' }}>
          <div>
            <span style={{ color: '#94A3B8' }}>Parent Span: </span>
            {parentSpan ? (
              <span style={{ color: '#F5F7FA' }}>
                <strong>{parentSpan.serviceName}</strong>: {parentSpan.name} (<code>{parentSpan.spanId}</code>)
              </span>
            ) : (
              <span style={{ color: '#94A3B8', italic: 'true' }}>None (Root Span)</span>
            )}
          </div>
          <div>
            <span style={{ color: '#94A3B8' }}>Direct Children: </span>
            <strong style={{ color: '#F5F7FA' }}>{children.length} span(s)</strong>
          </div>
        </div>
      </div>

      {/* Attributes & Message Section */}
      <div>
        <h4 style={{ fontSize: '0.8rem', color: '#38bdf8', textTransform: 'uppercase', margin: '0 0 0.5rem 0' }}>Attributes & Messages</h4>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.35rem', fontSize: '0.8rem' }}>
          <div>
            <span style={{ color: '#94A3B8' }}>Status Message: </span>
            <span style={{ color: statusMessage ? '#EF4444' : '#F5F7FA' }}>{statusMessage || 'None'}</span>
          </div>
        </div>
      </div>
    </div>
  )
}

export default SpanInspector
