function WorkspaceHeader({ eyebrow, title, subtitle }) {
  return (
    <header className="workspace-header" aria-labelledby="workspace-title">
      {eyebrow ? <p className="eyebrow">{eyebrow}</p> : null}
      <h1 id="workspace-title">{title}</h1>
      {subtitle ? <p className="dashboard-subtitle">{subtitle}</p> : null}
    </header>
  )
}

export default WorkspaceHeader
