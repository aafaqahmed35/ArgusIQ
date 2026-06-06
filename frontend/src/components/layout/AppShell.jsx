function AppShell({ children }) {
  return (
    <div className="app-shell">
      <main className="dashboard-page">{children}</main>
    </div>
  )
}

export default AppShell
