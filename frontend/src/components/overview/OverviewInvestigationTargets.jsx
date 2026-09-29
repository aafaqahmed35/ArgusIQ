import { Link } from 'react-router-dom'
import OverviewPanelState from './OverviewPanelState'
import { buildInvestigationTargets } from '../../pages/overviewData'

function TargetGroup({ title, items, emptyMessage }) {
  return (
    <section className="overview-targets__group" aria-label={title}>
      <div className="overview-targets__group-header">
        <h3>{title}</h3>
        <span>{items.length.toLocaleString()} shown</span>
      </div>
      {items.length === 0 ? (
        <p className="overview-targets__empty">{emptyMessage}</p>
      ) : (
        <ol className="overview-targets__list">
          {items.map((item) => (
            <li key={item.id}>
              <Link className="overview-targets__link" to={item.href}>
                <span className="overview-targets__copy">
                  <strong title={item.primary}>{item.primary}</strong>
                  <small>{item.secondary}</small>
                </span>
                <span className="overview-targets__value">{item.value}</span>
                <span className="overview-targets__arrow" aria-hidden="true">→</span>
              </Link>
            </li>
          ))}
        </ol>
      )}
    </section>
  )
}

function OverviewInvestigationTargets({ metrics, state, onRetry }) {
  const { failingEndpoints, slowOperations } = buildInvestigationTargets(metrics)

  return (
    <section className="analytics-panel overview-targets" aria-labelledby="overview-targets-title">
      <div className="analytics-panel__header">
        <div>
          <p className="section-kicker">Investigation queue</p>
          <h2 id="overview-targets-title">Where to Look Next</h2>
        </div>
        <Link className="panel-action" to="/traces">All traces</Link>
      </div>

      <OverviewPanelState
        state={state}
        loadingLabel="Loading investigation targets"
        errorTitle="Investigation targets unavailable"
        emptyMessage="Ranked endpoint and operation signals will appear with persisted telemetry."
        onRetry={onRetry}
      >
        <div className="overview-targets__body">
          <TargetGroup
            title="Failing endpoints"
            items={failingEndpoints}
            emptyMessage="No persisted endpoint errors reported."
          />
          <TargetGroup
            title="Slow operations"
            items={slowOperations}
            emptyMessage="No persisted operation latency reported."
          />
        </div>
      </OverviewPanelState>
    </section>
  )
}

export default OverviewInvestigationTargets
