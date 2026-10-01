import React from 'react'

function readableIssue(issue) {
  return String(issue || '').toLowerCase().replaceAll('_', ' ')
}

function CriticalPathBanner({ criticalPathInfo }) {
  if (!criticalPathInfo?.status) return null

  const {
    status,
    issues,
    algorithm,
    totalCriticalPathMs,
    traceWallClockMs,
    criticalPathPercentage,
    largestContributor,
    criticalPathNodes,
  } = criticalPathInfo
  const isUnavailable = status === 'UNAVAILABLE'

  return (
    <section className="critical-path-banner" aria-labelledby="critical-path-title">
      <div className="critical-path-banner__header">
        <div>
          <p className="section-kicker">Backend structural analysis</p>
          <h3 id="critical-path-title">Structural Critical Path</h3>
        </div>
        <span className={`critical-path-banner__status critical-path-banner__status--${status.toLowerCase()}`}>
          {status}
        </span>
      </div>

      {isUnavailable ? (
        <p className="critical-path-banner__unavailable">
          A structural path cannot be derived from the available trace graph.
        </p>
      ) : (
        <div className="critical-path-banner__metrics">
          <Metric label="Trace wall clock" value={`${traceWallClockMs} ms`} />
          <Metric label="Critical-path duration" value={`${totalCriticalPathMs} ms`} />
          <Metric label="Wall-clock coverage" value={`${criticalPathPercentage}%`} />
          <Metric
            label="Largest path contribution"
            value={largestContributor
              ? `${largestContributor.serviceName}: ${largestContributor.name} (${largestContributor.contributionDurationMs} ms)`
              : 'None'}
          />
        </div>
      )}

      <p className="critical-path-banner__method">
        Algorithm: <code>{algorithm || 'Unavailable'}</code>. Parent links and timestamps provide structural evidence;
        they do not prove synchronous waiting, causation, or root cause.
      </p>

      {issues.length > 0 ? (
        <p className="critical-path-banner__limitations">
          <strong>Evidence limitations:</strong> {issues.map(readableIssue).join(', ')}
        </p>
      ) : null}

      {criticalPathNodes.length > 0 ? (
        <div className="critical-path-banner__path">
          <strong>Ordered structural path</strong>
          <div className="critical-path-banner__spans">
            {criticalPathNodes.map((node, index) => (
              <React.Fragment key={node.spanId || index}>
                <span>
                  <strong>{node.serviceName}</strong>: {node.name}
                  {' '}({node.contributionDurationMs} ms contribution)
                </span>
                {index < criticalPathNodes.length - 1 ? <b aria-hidden="true">→</b> : null}
              </React.Fragment>
            ))}
          </div>
        </div>
      ) : null}
    </section>
  )
}

function Metric({ label, value }) {
  return (
    <div className="critical-path-banner__metric">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  )
}

export default CriticalPathBanner
