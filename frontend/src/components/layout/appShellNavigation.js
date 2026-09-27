export function mobileNavigationReducer(isOpen, action) {
  if (action.type === 'open') return true
  if (action.type === 'toggle') return !isOpen

  return false
}

export function getPrimaryWorkspaces(workspaces) {
  return workspaces.filter((workspace) => workspace.primaryNavigation !== false)
}
