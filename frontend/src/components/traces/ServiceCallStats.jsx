import { useMemo } from 'react'
import { calculateServiceStats } from '../../lib/traceMetrics'

function ServiceCallStats({ spans = [], totalTraceDurationMs = 1 }) {
  const serviceStats = useMemo(
    () => calculateServiceStats(spans, totalTraceDurationMs),
    [spans, totalTraceDurationMs]
  )

  if (serviceStats.length === 0) {
    return (
      <div className="trace-visualization-empty">
        No service telemetry available to compute statistics.
      </div>
    )
  }

  return (
    <section className="service-call-stats-panel" aria-labelledby="service-stats-title">
      <div>
        <p className="section-kicker">Derived from available spans</p>
        <h3 id="service-stats-title">Per-Service Span Durations</h3>
      </div>
      <p className="service-call-stats-panel__note">Shares use summed span durations and may exceed trace wall-clock time when spans overlap.</p>

      <div className="service-call-stats-panel__table">
        <table>
          <thead>
            <tr>
              <th scope="col">Rank</th>
              <th scope="col">Service</th>
              <th scope="col">Spans</th>
              <th scope="col">Average span</th>
              <th scope="col">Longest span</th>
              <th scope="col">Errors</th>
              <th scope="col">Summed duration share</th>
            </tr>
          </thead>
          <tbody>
            {serviceStats.map((item) => (
              <tr key={item.serviceName} className={item.isLargestDurationContributor ? 'is-largest-contributor' : ''}>
                <td>
                  #{item.rank}
                </td>
                <td>
                  <strong>{item.serviceName}</strong>
                  {item.isLargestDurationContributor ? <small>Largest summed duration</small> : null}
                </td>
                <td>{item.requestCount}</td>
                <td><code>{item.avgDurationMs} ms</code></td>
                <td><code>{item.maxDurationMs} ms</code></td>
                <td><span className={item.errorCount > 0 ? 'text-error' : ''}>{item.errorCount}</span></td>
                <td>
                  <div className="service-call-stats-panel__share">
                    <span className="service-call-stats-panel__bar" aria-hidden="true">
                      <span style={{ '--service-share': `${item.contributionPct}%` }} />
                    </span>
                    <code>{item.contributionPct}%</code>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  )
}

export default ServiceCallStats
