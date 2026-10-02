export const WORKSPACES = [
  {
    id: 'overview',
    path: '/',
    label: 'Overview',
    live: true,
  },
  {
    id: 'traces',
    path: '/traces',
    label: 'Traces',
    live: true,
  },
  {
    id: 'analytics',
    path: '/analytics',
    label: 'Analytics',
    live: true,
  },
  {
    id: 'services',
    path: '/services',
    label: 'Services',
    live: true,
  },
  {
    id: 'alerts',
    path: '/alerts',
    label: 'Alerts',
    live: true,
  },
  {
    id: 'infrastructure',
    path: '/infrastructure',
    label: 'Infrastructure',
    primaryNavigation: false,
  },
  {
    id: 'settings',
    path: '/settings',
    label: 'Settings',
    primaryNavigation: false,
  },
]

export function getWorkspaceById(id) {
  return WORKSPACES.find((workspace) => workspace.id === id)
}
