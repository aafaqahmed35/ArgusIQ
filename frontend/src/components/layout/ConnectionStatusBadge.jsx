function ConnectionStatusBadge({ status }) {
  const statusName = status || 'CONNECTING'

  return (
    <span className={`connection-status connection-status--${statusName.toLowerCase()}`}>
      {statusName}
    </span>
  )
}

export default ConnectionStatusBadge
