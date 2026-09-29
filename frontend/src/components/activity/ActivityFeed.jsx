import { Link } from 'react-router-dom'
import ActivityFeedItem from './ActivityFeedItem'
import { buildActivityItems } from '../../pages/overviewData'

const ACTIVITY_LIMIT = 20

function ActivityFeed({
  traces,
  isLoading = false,
  limit = ACTIVITY_LIMIT,
  actionHref = null,
  actionLabel = null,
  className = '',
  error = null,
  onRetry = null,
}) {
  const activities = buildActivityItems(traces, limit)

  return (
    <aside className={`activity-feed ${className}`.trim()} aria-labelledby="activity-feed-title">
      <div className="activity-feed__header">
        <div>
          <p className="section-kicker">Live monitor</p>
          <h2 id="activity-feed-title">Recent Activity</h2>
        </div>
        <div className="activity-feed__header-actions">
          {actionHref && actionLabel ? (
            <Link className="panel-action activity-feed__action" to={actionHref}>
              {actionLabel}
            </Link>
          ) : null}
          <span className="activity-feed__count">Latest {limit}</span>
        </div>
      </div>

      {isLoading ? (
        <div className="activity-feed__skeleton" aria-busy="true">
          <span className="activity-feed__skeleton-row skeleton-line skeleton-line--wide" />
          <span className="activity-feed__skeleton-row skeleton-line" />
          <span className="activity-feed__skeleton-row skeleton-line skeleton-line--short" />
        </div>
      ) : error && activities.length === 0 ? (
        <div className="activity-feed__empty activity-feed__empty--error" role="alert">
          <strong>Recent activity unavailable</strong>
          <span>ArgusIQ could not load the recent persisted trace window.</span>
          {onRetry ? (
            <button className="overview-panel-state__action" type="button" onClick={onRetry}>
              Retry
            </button>
          ) : null}
        </div>
      ) : activities.length === 0 ? (
        <div className="activity-feed__empty">
          <strong>No telemetry</strong>
          <span>Recent persisted traces will appear here after telemetry is received.</span>
        </div>
      ) : (
        <>
          <div className="activity-feed__columns" aria-hidden="true">
            <span>Time / status</span>
            <span>Service / operation</span>
            <span>Request</span>
            <span>Duration</span>
            <span>Investigate</span>
          </div>
          <ol className="activity-feed__list">
          {activities.map((activity, index) => (
            <ActivityFeedItem activity={activity} isLatest={index === 0} key={activity.id} />
          ))}
          </ol>
        </>
      )}
    </aside>
  )
}

export default ActivityFeed
