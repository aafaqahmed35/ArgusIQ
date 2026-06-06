import MetricCard from './MetricCard'

function MetricGrid({ metrics }) {
  return (
    <section className="metric-grid" aria-label="Trace summary">
      {metrics.map((metric) => (
        <MetricCard
          key={metric.label}
          label={metric.label}
          value={metric.value}
          detail={metric.detail}
          tone={metric.tone}
        />
      ))}
    </section>
  )
}

export default MetricGrid
