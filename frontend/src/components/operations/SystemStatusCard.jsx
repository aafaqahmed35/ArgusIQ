function SystemStatusCard({ status, tone, summary }) {
  return (
    <section className={`system-status-card system-status-card--${tone}`} aria-labelledby="system-status-title">
      <p className="section-kicker">System Status</p>
      <div className="system-status-card__body">
        <span className="system-status-card__indicator" aria-hidden="true" />
        <div>
          <h2 id="system-status-title">{status}</h2>
          <p>{summary}</p>
        </div>
      </div>
    </section>
  )
}

export default SystemStatusCard
