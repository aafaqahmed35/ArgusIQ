import assert from 'node:assert/strict'
import path from 'node:path'
import test, { after, before } from 'node:test'
import { fileURLToPath } from 'node:url'
import React from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter } from 'react-router-dom'
import { createServer } from 'vite'
import { WORKSPACES } from '../../config/workspaceConfig.js'
import { getPrimaryWorkspaces, mobileNavigationReducer } from './appShellNavigation.js'

const frontendRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../..')
let vite
let AppShell

before(async () => {
  vite = await createServer({
    root: frontendRoot,
    appType: 'custom',
    logLevel: 'silent',
    server: { middlewareMode: true },
  })

  ;({ default: AppShell } = await vite.ssrLoadModule('/src/components/layout/AppShell.jsx'))
})

after(async () => {
  await vite?.close()
})

test('the application shell exposes truthful primary navigation and active-route semantics', () => {
  const markup = renderToStaticMarkup(
    React.createElement(
      MemoryRouter,
      { initialEntries: ['/traces'] },
      React.createElement(AppShell),
    ),
  )

  assert.match(markup, /aria-label="Open primary navigation"/)
  assert.match(markup, /aria-expanded="false"/)
  assert.match(markup, /aria-current="page"[^>]*href="\/traces"/)
  assert.match(markup, /ArgusIQ workspace/)
  assert.doesNotMatch(markup, /Admin User|admin@argusiq\.io/)
  assert.doesNotMatch(markup, />Infrastructure<|>Settings</)
})

test('unfinished workspaces are omitted only from primary navigation', () => {
  const primaryWorkspaces = getPrimaryWorkspaces(WORKSPACES)

  assert.deepEqual(primaryWorkspaces.map(({ id }) => id), ['overview', 'traces', 'analytics', 'services', 'alerts'])
  assert.equal(WORKSPACES.some(({ id }) => id === 'infrastructure'), true)
  assert.equal(WORKSPACES.some(({ id }) => id === 'settings'), true)
})

test('mobile navigation closes for every supported exit path', () => {
  assert.equal(mobileNavigationReducer(false, { type: 'open' }), true)
  assert.equal(mobileNavigationReducer(true, { type: 'toggle' }), false)
  assert.equal(mobileNavigationReducer(true, { type: 'escape' }), false)
  assert.equal(mobileNavigationReducer(true, { type: 'route-selected' }), false)
  assert.equal(mobileNavigationReducer(true, { type: 'desktop-resize' }), false)
  assert.equal(mobileNavigationReducer(true, { type: 'backdrop' }), false)
})
