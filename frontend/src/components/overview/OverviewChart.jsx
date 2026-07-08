const DATE_FIELDS = ['timestamp', 'createdAt', 'startTime', 'endTime']

function getTraceTimestamp(trace) {
  const field = DATE_FIELDS.find(
    (fieldName) => trace?.[fieldName] !== undefined && trace?.[fieldName] !== null && trace?.[fieldName] !== '',
  )

  if (!field) {
    return null
  }

  const timestamp = new Date(trace[field]).getTime()
  return Number.isFinite(timestamp) ? timestamp : null
}

function buildVolumeBuckets(traces, bucketCount = 16) {
  const timestamps = traces.map(getTraceTimestamp).filter((timestamp) => timestamp !== null)

  if (timestamps.length === 0) {
    return []
  }

  const minTime = Math.min(...timestamps)
  const maxTime = Math.max(...timestamps)
  const range = Math.max(maxTime - minTime, 1)
  const buckets = Array.from({ length: bucketCount }, (_, index) => ({
    index,
    count: 0,
  }))

  timestamps.forEach((timestamp) => {
    const bucketIndex = Math.min(Math.floor(((timestamp - minTime) / range) * bucketCount), bucketCount - 1)
    buckets[bucketIndex].count += 1
  })

  const peakCount = Math.max(...buckets.map((bucket) => bucket.count), 1)

  return buckets.map((bucket) => ({
    ...bucket,
    share: (bucket.count / peakCount) * 100,
  }))
}

function OverviewChart({ traces, isLoading = false }) {
  const buckets = buildVolumeBuckets(traces)
  const totalRequests = traces.length

  return (
    <section className="analytics-panel overview-chart" aria-labelledby="overview-request-volume-title">
      <div className="analytics-panel__header">
        <div>
          <p className="section-kicker">Traffic</p>
          <h2 id="overview-request-volume-title">Request Volume</h2>
        </div>
        <span className="panel-action">{totalRequests.toLocaleString()} loaded</span>
      </div>

      {isLoading ? (
        <div className="analytics-skeleton-list" aria-busy="true">
          <span className="skeleton-line skeleton-line--wide" />
          <span className="skeleton-line" />
          <span className="skeleton-line skeleton-line--short" />
        </div>
      ) : buckets.length === 0 ? (
        <div className="analytics-empty">
          <strong>No request volume yet</strong>
          <span>Volume bars appear after traces are loaded.</span>
        </div>
      ) : (
        <div className="overview-chart__body">
          <div className="overview-chart__bars" aria-hidden="true">
            {buckets.map((bucket) => (
              <span
                className="overview-chart__bar"
                key={bucket.index}
                style={{ '--volume-share': `${Math.max(bucket.share, 4)}%` }}
                title={`${bucket.count.toLocaleString()} requests`}
              />
            ))}
          </div>
          <p className="overview-chart__caption">Distribution from loaded traces</p>
        </div>
      )}
    </section>
  )
}

export default OverviewChart
