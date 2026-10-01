import { useMemo } from 'react'

function MiniTraceMap({ spans = [], traceSummary = {} }) {
  const topology = useMemo(() => {
    if (!Array.isArray(spans) || spans.length === 0) {
      return { services: traceSummary?.serviceName ? [traceSummary.serviceName] : [], edges: [] }
    }

    const services = new Set()
    const edgesMap = new Map()
    const spanMap = new Map(spans.filter((span) => span.spanId).map((span) => [span.spanId, span]))

    spans.forEach((span) => {
      const service = span.serviceName || traceSummary?.serviceName
      if (service) services.add(service)

      const parent = span.parentSpanId ? spanMap.get(span.parentSpanId) : null
      const parentService = parent?.serviceName || traceSummary?.serviceName
      if (!parentService || !service || parentService === service) return

      const edgeKey = `${parentService}\u0000${service}`
      edgesMap.set(edgeKey, (edgesMap.get(edgeKey) || 0) + 1)
    })

    return {
      services: Array.from(services).sort(),
      edges: Array.from(edgesMap.entries())
        .map(([key, count]) => {
          const [from, to] = key.split('\u0000')
          return { from, to, count }
        })
        .sort((a, b) => a.from.localeCompare(b.from) || a.to.localeCompare(b.to)),
    }
  }, [spans, traceSummary])

  if (topology.services.length === 0) {
    return <div className="trace-visualization-empty">No service identity is available for this trace.</div>
  }

  return (
    <section className="trace-topology" aria-labelledby="trace-topology-title">
      <div>
        <p className="section-kicker">Observed span relationships</p>
        <h3 id="trace-topology-title">Service Communication</h3>
      </div>

      <ul className="trace-topology__services" aria-label="Services observed in trace">
        {topology.services.map((service) => <li key={service}>{service}</li>)}
      </ul>

      <div className="trace-topology__edges">
        <h4>Validated cross-service parent/child edges</h4>
        {topology.edges.length > 0 ? (
          <ul>
            {topology.edges.map((edge) => (
              <li key={`${edge.from}-${edge.to}`}>
                <code>{edge.from}</code> <span aria-hidden="true">→</span> <code>{edge.to}</code>
                <span>{edge.count} observed {edge.count === 1 ? 'edge' : 'edges'}</span>
              </li>
            ))}
          </ul>
        ) : (
          <p>No cross-service parent/child edge is present in the available spans.</p>
        )}
      </div>
      <p className="trace-topology__note">This view reports observed structural edges only; it does not infer calls or causation.</p>
    </section>
  )
}

export default MiniTraceMap
