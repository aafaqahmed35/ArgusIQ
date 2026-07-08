import MetricCard from './MetricCard'

function MetricGrid({ metrics, isLoading = false, className = '' }) {
  return (
    <section className={`metric-grid ${className}`.trim()} aria-label="Trace summary">
      {metrics.map((metric) => (
        <MetricCard
          key={metric.label}
          label={metric.label}
          value={metric.value}
          detail={metric.detail}
          tone={metric.tone}
          isLoading={isLoading}
        />
      ))}
    </section>
  )
}

export default MetricGrid
