function AnalyticsSummaryStrip({ items, isLoading = false }) {
  if (isLoading) {
    return (
      <section className="analytics-summary-strip" aria-label="Analytics summary" aria-busy="true">
        <div className="analytics-summary-strip__skeleton">
          <span className="skeleton-line skeleton-line--short" />
          <span className="skeleton-line skeleton-line--wide" />
          <span className="skeleton-line skeleton-line--short" />
        </div>
      </section>
    )
  }

  return (
    <section className="analytics-summary-strip" aria-label="Analytics summary">
      <dl className="analytics-summary-strip__grid">
        {items.map((item) => (
          <div className="analytics-summary-strip__item" key={item.label}>
            <dt>{item.label}</dt>
            <dd title={item.detail}>{item.value}</dd>
          </div>
        ))}
      </dl>
    </section>
  )
}

export default AnalyticsSummaryStrip
