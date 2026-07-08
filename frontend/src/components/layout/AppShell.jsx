import { NavLink, Outlet } from 'react-router-dom'
import { WORKSPACES } from '../../config/workspaceConfig'

function AppShell() {
  return (
    <div className="app-shell">
      <aside className="app-sidebar" aria-label="Primary navigation">
        <div className="app-sidebar__brand">
          <span className="app-sidebar__mark" aria-hidden="true" />
          <div>
            <strong>ArgusIQ</strong>
            <span>Observability</span>
          </div>
        </div>

        <nav className="app-sidebar__nav">
          {WORKSPACES.map((workspace) => (
            <NavLink
              className={({ isActive }) => `app-sidebar__link ${isActive ? 'is-active' : ''}`}
              end={workspace.path === '/'}
              key={workspace.id}
              to={workspace.path}
            >
              <span className="app-sidebar__icon" aria-hidden="true" />
              <span>{workspace.label}</span>
            </NavLink>
          ))}
        </nav>

        <div className="app-sidebar__user">
          <span className="app-sidebar__avatar" aria-hidden="true">
            AD
          </span>
          <div>
            <strong>Admin User</strong>
            <span>admin@argusiq.io</span>
          </div>
        </div>
      </aside>

      <main className="dashboard-page">
        <Outlet />
      </main>
    </div>
  )
}

export default AppShell
