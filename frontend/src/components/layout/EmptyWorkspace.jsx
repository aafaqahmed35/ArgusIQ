import { getWorkspaceById } from '../../config/workspaceConfig'
import WorkspaceHeader from './WorkspaceHeader'

function EmptyWorkspace({ workspaceId }) {
  const workspace = getWorkspaceById(workspaceId)

  if (!workspace) {
    return null
  }

  return (
    <section className="empty-workspace" aria-label={`${workspace.title} workspace`}>
      <WorkspaceHeader eyebrow={workspace.eyebrow} title={workspace.title} subtitle={workspace.subtitle} />
      <div className="empty-workspace__panel">
        <p className="section-kicker">Planned workspace</p>
        <strong className="empty-workspace__problem">{workspace.problem}</strong>
        <p className="empty-workspace__message">
          This workspace is under construction. The full experience ships in a future release.
        </p>
        <span className="empty-workspace__phase">Scheduled for {workspace.scheduledPhase}</span>
      </div>
    </section>
  )
}

export default EmptyWorkspace
