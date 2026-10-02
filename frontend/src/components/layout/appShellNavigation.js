export function mobileNavigationReducer(isOpen, action) {
  if (action.type === 'open') return true
  if (action.type === 'toggle') return !isOpen

  return false
}

export function getPrimaryWorkspaces(workspaces) {
  return workspaces.filter((workspace) => workspace.primaryNavigation !== false)
}
export function focusMobileDestination(mainContent, viewport = window) {
  if (!mainContent) return

  viewport.scrollTo({ top: 0, left: 0, behavior: 'instant' })
  mainContent.focus({ preventScroll: true })
}
