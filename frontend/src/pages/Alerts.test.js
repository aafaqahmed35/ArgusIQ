import assert from 'node:assert/strict'
import path from 'node:path'
import test, { after, before } from 'node:test'
import { fileURLToPath } from 'node:url'
import React from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { createServer } from 'vite'

const frontendRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..')
let vite
let Alerts
let TraceContext

before(async () => {
  vite = await createServer({
    root: frontendRoot,
    appType: 'custom',
    logLevel: 'silent',
    server: { middlewareMode: true },
  })

  ;({ default: Alerts } = await vite.ssrLoadModule('/src/pages/Alerts.jsx'))
  ;({ TraceContext } = await vite.ssrLoadModule('/src/context/traceContextCore.js'))
})

after(async () => {
  await vite?.close()
})

test('Alerts renders the shared WebSocket state without replacing it with local state', () => {
  for (const status of ['CONNECTING', 'LIVE', 'ERROR']) {
    const markup = renderToStaticMarkup(
      React.createElement(
        TraceContext.Provider,
        { value: { websocketStatus: status } },
        React.createElement(Alerts),
      ),
    )

    assert.match(markup, new RegExp(`connection-status--${status.toLowerCase()}`))
    assert.match(markup, new RegExp(`>${status}</span>`))
  }
})
