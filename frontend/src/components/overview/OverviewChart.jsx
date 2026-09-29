import OverviewPanelState from './OverviewPanelState'
import { buildDistribution, LATENCY_BUCKET_ORDER } from '../../pages/overviewData'

function OverviewChart({ metrics, state, onRetry }) {
  const distribution = buildDistribution(metrics?.traceDurationHistogram, LATENCY_BUCKET_ORDER)
  const observationCount = distribution.reduce((sum, bucket) => sum + bucket.count, 0)
  const peakCount = Math.max(...distribution.map((bucket) => bucket.count), 1)

  return (
    <section className="analytics-panel overview-chart" aria-labelledby="overview-latency-distribution-title">
      <div className="analytics-panel__header">
        <div>
          <p className="section-kicker">Persisted latency</p>
          <h2 id="overview-latency-distribution-title">Duration Distribution</h2>
        </div>
        <span className="panel-action">{observationCount.toLocaleString()} traces</span>
      </div>

      <OverviewPanelState
        state={state}
        loadingLabel="Loading persisted duration distribution"
        errorTitle="Latency distribution unavailable"
        emptyMessage="Latency buckets will populate after trace data is persisted."
        onRetry={onRetry}
      >
        <div className="overview-distribution" aria-label="Trace duration histogram">
          <ol className="overview-distribution__list">
            {distribution.map((bucket) => (
              <li className="overview-distribution__item" key={bucket.label}>
                <span className="overview-distribution__label">{bucket.label}</span>
                <span className="overview-distribution__track" aria-hidden="true">
                  <span
                    className="overview-distribution__bar"
                    style={{ '--distribution-width': `${Math.max((bucket.count / peakCount) * 100, 3)}%` }}
                  />
                </span>
                <strong>{bucket.count.toLocaleString()}</strong>
                <span className="overview-distribution__share">{bucket.share.toFixed(1)}%</span>
              </li>
            ))}
          </ol>
          <p className="overview-distribution__caption">
            Persisted trace counts by duration bucket. Values reflect the metrics endpoint, not a synthetic time series.
          </p>
        </div>
      </OverviewPanelState>
    </section>
  )
}

export default OverviewChart
