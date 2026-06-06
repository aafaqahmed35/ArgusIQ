function TraceDetailSection({ title, children }) {
  return (
    <section className="trace-detail-section">
      <h3>{title}</h3>
      {children}
    </section>
  )
}

export default TraceDetailSection
