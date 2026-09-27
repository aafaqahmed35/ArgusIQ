function ConnectionStatusBadge({ status }) {
  const statusName = status || 'CONNECTING'

  return (
    <span
      aria-label={`Live connection status: ${statusName}`}
      aria-live="polite"
      className={`connection-status connection-status--${statusName.toLowerCase()}`}
      role="status"
    >
      {statusName}
    </span>
  )
}

export default ConnectionStatusBadge
