function ActivityFeedItem({ activity }) {
  return (
    <li className="activity-feed-item">
      <div className="activity-feed-item__topline">
        <span className="activity-feed-item__time">{activity.timestamp}</span>
        <span className={activity.statusClass}>{activity.status}</span>
      </div>
      <div className="activity-feed-item__request">
        <span className="activity-feed-item__method">{activity.method}</span>
        <span className="activity-feed-item__path">{activity.path}</span>
      </div>
      <span className="activity-feed-item__duration">{activity.duration}</span>
    </li>
  )
}

export default ActivityFeedItem
