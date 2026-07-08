function SystemSnapshot({ items }) {
  return (
    <section className="system-snapshot" aria-labelledby="system-snapshot-title">
      <div className="operations-panel__header">
        <div>
          <p className="section-kicker">System Snapshot</p>
          <h2 id="system-snapshot-title">Runtime View</h2>
        </div>
      </div>

      <dl className="system-snapshot__grid">
        {items.map((item) => (
          <div className={`system-snapshot__item system-snapshot__item--${item.tone}`} key={item.label}>
            <dt>{item.label}</dt>
            <dd>{item.value}</dd>
          </div>
        ))}
      </dl>
    </section>
  )
}

export default SystemSnapshot
