function HealthSignals({ signals }) {
  return (
    <section className="health-signals" aria-labelledby="health-signals-title">
      <div className="operations-panel__header">
        <div>
          <p className="section-kicker">Health Signals</p>
          <h2 id="health-signals-title">Operational Signals</h2>
        </div>
      </div>

      <div className="health-signals__grid">
        {signals.map((signal) => (
          <article className={`health-signal health-signal--${signal.tone}`} key={signal.label}>
            <span className="health-signal__label">{signal.label}</span>
            <strong>{signal.value}</strong>
            <span className="health-signal__detail">{signal.detail}</span>
          </article>
        ))}
      </div>
    </section>
  )
}

export default HealthSignals
