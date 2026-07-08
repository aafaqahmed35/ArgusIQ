import { Link } from 'react-router-dom'
import ActivityFeedItem from './ActivityFeedItem'

const DATE_FIELDS = ['timestamp', 'createdAt', 'startTime', 'endTime']
const STATUS_FIELDS = ['status', 'statusCode', 'httpStatus']
const METHOD_FIELDS = ['method', 'httpMethod', 'requestMethod']
const PATH_FIELDS = ['requestUri', 'path', 'endpoint', 'uri', 'url']
const DURATION_FIELDS = ['executionTimeMs', 'responseTime', 'duration', 'latency', 'responseTimeMs']
const ACTIVITY_LIMIT = 20

function getFieldValue(trace, fields, fallback = '—') {
  const key = fields.find((field) => trace?.[field] !== undefined && trace?.[field] !== null && trace?.[field] !== '')
  return key ? trace[key] : fallback
}

function getTraceTimestamp(trace) {
  const value = getFieldValue(trace, DATE_FIELDS, null)
  const timestamp = value ? new Date(value).getTime() : Number.NaN

  return {
    raw: value,
    sortValue: Number.isNaN(timestamp) ? null : timestamp,
  }
}

function formatTimestamp(value) {
  if (!value) {
    return '—'
  }

  const date = new Date(value)

  if (Number.isNaN(date.getTime())) {
    return String(value)
  }

  return new Intl.DateTimeFormat('en', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  }).format(date)
}

function formatDuration(value) {
  if (value === '—') {
    return value
  }

  const numericValue = Number(value)

  if (Number.isNaN(numericValue)) {
    return String(value)
  }

  return `${numericValue.toLocaleString()} ms`
}

function getStatusClass(status) {
  const numericStatus = Number(status)

  if (Number.isNaN(numericStatus)) {
    return 'activity-status activity-status--neutral'
  }

  if (numericStatus >= 500) {
    return 'activity-status activity-status--error'
  }

  if (numericStatus >= 400) {
    return 'activity-status activity-status--warning'
  }

  return 'activity-status activity-status--success'
}

function getActivityItems(traces, limit) {
  return traces
    .map((trace, index) => {
      const timestamp = getTraceTimestamp(trace)
      const status = getFieldValue(trace, STATUS_FIELDS)

      return {
        id: trace.id ?? trace.traceId ?? `${getFieldValue(trace, PATH_FIELDS)}-${timestamp.raw ?? index}`,
        index,
        sortValue: timestamp.sortValue,
        timestamp: formatTimestamp(timestamp.raw),
        method: getFieldValue(trace, METHOD_FIELDS),
        path: getFieldValue(trace, PATH_FIELDS),
        duration: formatDuration(getFieldValue(trace, DURATION_FIELDS)),
        status,
        statusClass: getStatusClass(status),
      }
    })
    .sort((left, right) => {
      if (left.sortValue !== null && right.sortValue !== null) {
        return right.sortValue - left.sortValue
      }

      if (left.sortValue !== null) {
        return -1
      }

      if (right.sortValue !== null) {
        return 1
      }

      return left.index - right.index
    })
    .slice(0, limit)
}

function ActivityFeed({
  traces,
  isLoading = false,
  limit = ACTIVITY_LIMIT,
  actionHref = null,
  actionLabel = null,
  className = '',
}) {
  const activities = getActivityItems(traces, limit)

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
      ) : activities.length === 0 ? (
        <div className="activity-feed__empty">
          <strong>No live activity yet</strong>
          <span>Recent trace events will appear here as telemetry arrives.</span>
        </div>
      ) : (
        <ol className="activity-feed__list">
          {activities.map((activity, index) => (
            <ActivityFeedItem activity={activity} isLatest={index === 0} key={activity.id} />
          ))}
        </ol>
      )}
    </aside>
  )
}

export default ActivityFeed
