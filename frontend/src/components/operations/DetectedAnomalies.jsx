function DetectedAnomalies({ anomalies }) {
  return (
    <section className="detected-anomalies" aria-labelledby="detected-anomalies-title">
      <div className="operations-panel__header">
        <div>
          <p className="section-kicker">Detected Anomalies</p>
          <h2 id="detected-anomalies-title">Current Findings</h2>
        </div>
      </div>

      {anomalies.length === 0 ? (
        <div className="detected-anomalies__empty detected-anomalies__empty--stable">
          <strong>System Stable</strong>
          <span>No anomalies detected.</span>
          <small>All monitored frontend signals are operating normally.</small>
        </div>
      ) : (
        <ul className="detected-anomalies__list">
          {anomalies.map((anomaly) => (
            <li className="detected-anomalies__item" key={anomaly}>
              {anomaly}
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

export default DetectedAnomalies
