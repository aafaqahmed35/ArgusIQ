import { useRef } from 'react'
import { formatDuration } from '../../lib/traceAggregation'

const SORT_FIELDS = [
  { value: 'traffic', label: 'Traffic' },
  { value: 'latency', label: 'Latency' },
]

function formatTrafficShare(value) {
  if (value === null || value === undefined) {
    return '—'
  }

  return `${value.toLocaleString(undefined, { maximumFractionDigits: 1 })}%`
}

function ServiceGroupsPanel({
  groups,
  isLoading = false,
  error = null,
  selectedGroupKey = null,
  onGroupSelect,
  sortField = 'traffic',
  onSortFieldChange,
  totalRequests = 0,
  groupCount = 0,
  recentTraceLimit = 100,
}) {
  const rowRefs = useRef([])

  const focusRow = (index) => {
    rowRefs.current[index]?.focus()
  }

  const handleRowKeyDown = (event, group, index) => {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault()
      onGroupSelect?.(group)
      return
    }

    if (event.key === 'ArrowDown') {
      event.preventDefault()
      const nextGroup = groups[index + 1]

      if (nextGroup) {
        onGroupSelect?.(nextGroup)
        focusRow(index + 1)
      }

      return
    }

    if (event.key === 'ArrowUp') {
      event.preventDefault()
      const previousGroup = groups[index - 1]

      if (previousGroup) {
        onGroupSelect?.(previousGroup)
        focusRow(index - 1)
      }
    }
  }

  const sourceNote = `Based on ${Number(totalRequests).toLocaleString()} recent traces (limit ${recentTraceLimit}) · ${Number(groupCount).toLocaleString()} URI groups`

  return (
    <section
      className={`analytics-panel service-groups-panel ${isLoading ? 'analytics-panel--loading' : ''}`}
      aria-labelledby="service-groups-title"
    >
      <div className="analytics-panel__header">
        <div>
          <p className="section-kicker">Service Groups</p>
          <h2 id="service-groups-title">Service Groups</h2>
          <span className="analytics-panel__source-note">{sourceNote}</span>
        </div>
        <div className="analytics-panel__sort-group">
          {SORT_FIELDS.map((field) => (
            <button
              className={`analytics-panel__sort ${sortField === field.value ? 'is-active' : ''}`}
              type="button"
              key={field.value}
              onClick={() => onSortFieldChange?.(field.value)}
            >
              {field.label}
              {sortField === field.value ? ' ▼' : ''}
            </button>
          ))}
        </div>
      </div>

      {isLoading ? (
        <div className="table-state table-state--skeleton" role="status" aria-busy="true">
          <span className="skeleton-line skeleton-line--wide" />
          <span className="skeleton-line" />
          <span className="skeleton-line skeleton-line--wide" />
          <span className="skeleton-line skeleton-line--short" />
        </div>
      ) : error ? (
        <div className="table-state table-state--error" role="alert">
          Unable to load traces from the backend.
        </div>
      ) : groups.length === 0 ? (
        <div className="analytics-empty">
          <strong>No service groups yet</strong>
          <span>URI groups will appear after recent traces are received.</span>
        </div>
      ) : (
        <div className="table-shell service-groups-panel__table-shell">
          <table className="trace-table service-groups-table">
            <thead>
              <tr>
                <th scope="col">Service Group</th>
                <th scope="col">Traffic %</th>
                <th scope="col">Requests</th>
                <th scope="col">Average RT</th>
                <th scope="col">P95</th>
              </tr>
            </thead>
            <tbody>
              {groups.map((group, index) => {
                const isSelected = selectedGroupKey === group.key
                const rowClassName = [
                  'trace-table__row',
                  'trace-table__row--interactive',
                  isSelected ? 'trace-table__row--selected' : '',
                ]
                  .filter(Boolean)
                  .join(' ')

                return (
                  <tr
                    className={rowClassName}
                    key={group.key}
                    ref={(element) => {
                      rowRefs.current[index] = element
                    }}
                    onClick={() => onGroupSelect?.(group)}
                    onKeyDown={(event) => handleRowKeyDown(event, group, index)}
                    role="button"
                    tabIndex={0}
                  >
                    <td className="cell-strong cell-path" title={group.name}>
                      {group.name}
                    </td>
                    <td>{formatTrafficShare(group.trafficShare)}</td>
                    <td>{group.requestCount.toLocaleString()}</td>
                    <td>{formatDuration(group.averageResponseTime)}</td>
                    <td>{formatDuration(group.p95ResponseTime)}</td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}

export default ServiceGroupsPanel
