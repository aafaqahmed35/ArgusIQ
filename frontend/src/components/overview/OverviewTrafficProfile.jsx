import OverviewPanelState from './OverviewPanelState'
import { buildDistribution } from '../../pages/overviewData'

function getStatusTone(label) {
  const status = String(label).toUpperCase()
  const numericStatus = Number(status)

  if (status.includes('ERROR') || (!Number.isNaN(numericStatus) && numericStatus >= 500)) {
    return 'error'
  }

  if (status.includes('UNSET') || (!Number.isNaN(numericStatus) && numericStatus >= 400)) {
    return 'warning'
  }

  if (status.includes('OK') || (!Number.isNaN(numericStatus) && numericStatus >= 200 && numericStatus < 400)) {
    return 'success'
  }

  return 'neutral'
}

function DistributionGroup({ title, items, getTone = () => 'signal' }) {
  const visibleItems = items.slice(0, 6)

  return (
    <section className="overview-traffic-profile__group" aria-label={title}>
      <div className="overview-traffic-profile__group-header">
        <h3>{title}</h3>
        <span>{visibleItems.reduce((sum, item) => sum + item.count, 0).toLocaleString()} observed</span>
      </div>
      {visibleItems.length === 0 ? (
        <p className="overview-traffic-profile__empty">No distribution values reported.</p>
      ) : (
        <dl className="overview-traffic-profile__list">
          {visibleItems.map((item) => (
            <div className="overview-traffic-profile__item" key={item.label}>
              <dt>
                <span className={`overview-traffic-profile__dot overview-traffic-profile__dot--${getTone(item.label)}`} aria-hidden="true" />
                {item.label}
              </dt>
              <dd>
                <strong>{item.count.toLocaleString()}</strong>
                <span>{item.share.toFixed(1)}%</span>
              </dd>
            </div>
          ))}
        </dl>
      )}
    </section>
  )
}

function OverviewTrafficProfile({ metrics, state, onRetry }) {
  const statusItems = buildDistribution(metrics?.statusCodeDistribution)
  const methodItems = buildDistribution(metrics?.httpMethodDistribution)

  return (
    <section className="analytics-panel overview-traffic-profile" aria-labelledby="overview-traffic-profile-title">
      <div className="analytics-panel__header">
        <div>
          <p className="section-kicker">Request composition</p>
          <h2 id="overview-traffic-profile-title">Traffic Profile</h2>
        </div>
        <span className="panel-action">Persisted</span>
      </div>

      <OverviewPanelState
        state={state}
        loadingLabel="Loading persisted traffic profile"
        errorTitle="Traffic profile unavailable"
        emptyMessage="HTTP method and status distributions will appear with persisted telemetry."
        onRetry={onRetry}
      >
        <div className="overview-traffic-profile__body">
          <DistributionGroup title="Trace status" items={statusItems} getTone={getStatusTone} />
          <DistributionGroup title="HTTP method" items={methodItems} />
        </div>
      </OverviewPanelState>
    </section>
  )
}

export default OverviewTrafficProfile
