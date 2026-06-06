function TopEndpointsPanel({ endpoints }) {
  return (
    <section className="analytics-panel" aria-labelledby="top-endpoints-title">
      <div className="analytics-panel__header">
        <div>
          <p className="section-kicker">Traffic</p>
          <h2 id="top-endpoints-title">Top Endpoints</h2>
        </div>
      </div>

      {endpoints.length === 0 ? (
        <div className="analytics-empty">No endpoint traffic available.</div>
      ) : (
        <ol className="endpoint-list">
          {endpoints.map((endpoint) => (
            <li className="endpoint-list__item" key={endpoint.endpoint}>
              <span className="endpoint-list__path">{endpoint.endpoint}</span>
              <strong>{endpoint.count.toLocaleString()}</strong>
            </li>
          ))}
        </ol>
      )}
    </section>
  )
}

export default TopEndpointsPanel
