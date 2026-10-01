import { backendUtcEpochMillis } from '../../lib/backendDateTime'

function statusTone(statusCode) {
  const code = String(statusCode || 'UNKNOWN').toUpperCase()
  if (code === 'ERROR' || code === '5XX' || code === '500') return 'error'
  if (code === 'WARN' || code === 'UNSET' || code === '4XX' || code === '400') return 'warning'
  if (code === 'OK') return 'success'
  return 'neutral'
}

function InvestigationTimeline({ spans = [], traceSummary = {}, onSelectSpan }) {
  if (!Array.isArray(spans) || spans.length === 0) {
    return <div className="trace-visualization-empty">No span starts are available for the timeline.</div>
  }

  const observedStartTimes = spans
    .map((span) => backendUtcEpochMillis(span.startTime))
    .filter((value) => value !== null)
  const minStart = observedStartTimes.length > 0 ? Math.min(...observedStartTimes) : 0
  const timelineEvents = spans.map((span) => {
    const startTime = backendUtcEpochMillis(span.startTime)
    return {
      ...span,
      offsetMs: startTime !== null ? Math.max(0, startTime - minStart) : 0,
      durationMs: span.durationMs ?? null,
    }
  }).sort((a, b) => a.offsetMs - b.offsetMs)

  return (
    <section className="investigation-timeline-panel" aria-labelledby="trace-timeline-title">
      <div className="investigation-timeline-panel__header">
        <div>
          <p className="section-kicker">Start-time order</p>
          <h3 id="trace-timeline-title">Observed Span Starts</h3>
        </div>
        <span>{timelineEvents.length} observed events</span>
      </div>
      <p className="investigation-timeline-panel__note">
        Chronological ordering reflects recorded start timestamps; it does not prove sequential execution or causation.
      </p>

      <div className="investigation-timeline-panel__events">
        {timelineEvents.map((event, index) => {
          const tone = statusTone(event.statusCode)
          const service = event.serviceName || traceSummary?.serviceName || 'Unknown service'
          return (
            <button
              type="button"
              key={event.spanId || `${event.name}-${index}`}
              className="investigation-timeline-event"
              onClick={() => onSelectSpan?.(event)}
            >
              <span className="investigation-timeline-event__offset">+{event.offsetMs} ms</span>
              <span className={`span-status-dot span-status-dot--${tone}`} aria-hidden="true" />
              <span className="investigation-timeline-event__body">
                <span>
                  <strong>{service}</strong>
                  <b>{event.name || 'Unnamed span'}</b>
                  <code>{event.durationMs === null ? 'Duration unavailable' : `${event.durationMs} ms`}</code>
                </span>
                <small>Kind: {event.kind || 'UNKNOWN'} · Status: {event.statusCode || 'UNKNOWN'}</small>
              </span>
            </button>
          )
        })}
      </div>
    </section>
  )
}

export default InvestigationTimeline
