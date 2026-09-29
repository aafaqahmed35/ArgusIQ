import { Link } from 'react-router-dom'

function MetricCard({ label, value, detail, tone = 'default', isLoading = false, href = null }) {

  if (isLoading) {
    return (
      <article className={`metric-card metric-card--${tone} metric-card--loading`} aria-busy="true">
        <span className="metric-card__icon" aria-hidden="true" />
        <span className="metric-card__skeleton metric-card__skeleton--label" />
        <span className="metric-card__skeleton metric-card__skeleton--value" />
        <span className="metric-card__skeleton metric-card__skeleton--detail" />
      </article>
    )
  }

  const content = (
    <article className={`metric-card metric-card--${tone}`}>
      <span className="metric-card__marker" aria-hidden="true" />
      <span className="metric-card__icon" aria-hidden="true" />
      <div className="metric-card__content">
        <span className="metric-card__label">{label}</span>
        <strong className="metric-card__value" title={String(value)}>
          {value}
        </strong>
        {detail ? <span className="metric-card__detail">{detail}</span> : null}
      </div>
    </article>
  )

  return href ? (
    <Link className="metric-card__link" to={href} aria-label={`${label}: ${value}`}>
      {content}
    </Link>
  ) : (
    content
  )
}

export default MetricCard
