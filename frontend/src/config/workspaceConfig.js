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
    title: 'Infrastructure',
    eyebrow: 'Pipeline health',
    subtitle: 'Verify REST, WebSocket, and backend connectivity for trustworthy telemetry.',
    problem: 'Are my observability pipelines working?',
    scheduledPhase: 'PR-7',
  },
  {
    id: 'settings',
    path: '/settings',
    label: 'Settings',
    title: 'Settings',
    eyebrow: 'Configuration',
    subtitle: 'Manage connection settings and workspace preferences.',
    problem: 'How is ArgusIQ configured for my environment?',
    scheduledPhase: 'PR-8',
  },
]

export function getWorkspaceById(id) {
  return WORKSPACES.find((workspace) => workspace.id === id)
}
