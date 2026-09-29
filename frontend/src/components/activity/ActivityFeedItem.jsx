import { Link } from 'react-router-dom'

function ActivityFeedItem({ activity, isLatest = false }) {
  return (
    <li className={`activity-feed-item ${isLatest ? 'activity-feed-item--latest' : ''}`}>
      <div className="activity-feed-item__topline">
        <span className="activity-feed-item__time">{activity.timestamp}</span>
        <span className={`activity-status activity-status--${activity.statusTone}`}>{activity.status}</span>
      </div>
      <div className="activity-feed-item__context">
        <strong title={activity.service}>{activity.service}</strong>
        <span title={activity.operation}>{activity.operation}</span>
      </div>
      <div className="activity-feed-item__request">
        <span className="activity-feed-item__method">{activity.method}</span>
        <span className="activity-feed-item__path" title={activity.path}>
          {activity.path}
        </span>
      </div>
      <span className="activity-feed-item__duration">{activity.duration}</span>
      <Link className="activity-feed-item__inspect" to={activity.href} aria-label={`Inspect trace ${activity.traceId ?? activity.path}`}>
        Inspect <span aria-hidden="true">→</span>
      </Link>
    </li>
  )
}

export default ActivityFeedItem
