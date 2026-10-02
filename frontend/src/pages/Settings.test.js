import assert from 'node:assert/strict'
import test, { before, after } from 'node:test'
import React from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter } from 'react-router-dom'
import { createServer } from 'vite'

let vite, Settings, runtime
before(async () => {
  vite = await createServer({ appType: 'custom', logLevel: 'silent', server: { middlewareMode: true } })
  ;({ default: Settings } = await vite.ssrLoadModule('/src/pages/Settings.jsx'))
  runtime = await vite.ssrLoadModule('/src/config/runtimeConfig.js')
})
after(async () => { await vite?.close() })
function render() { return renderToStaticMarkup(React.createElement(MemoryRouter, null, React.createElement(Settings))) }
test('Settings shows actual client configuration without claiming reachability or backend configuration', () => {
  const html = render()
  assert.ok(html.includes(runtime.endpointLabel(runtime.API_BASE_URL)))
  assert.ok(html.includes(runtime.endpointLabel(runtime.WEBSOCKET_URL)))
  assert.match(html, /\/topic\/traces/)
  assert.match(html, /Build-time configuration/)
  assert.match(html, /do not confirm reachability/)
  assert.match(html, /Backend timestamps are interpreted as UTC/)
})
test('Settings is read-only with no fake editable settings or authenticated identity', () => {
  const html = render()
  assert.match(html, /Read-only/)
  assert.match(html, /No editable client preferences/)
  assert.match(html, /Account identity, organizations, roles/)
  assert.doesNotMatch(html, /<form|<input|<select|<button|Admin User|admin@|Signed in|Save changes|under construction|Scheduled for/)
})
test('Settings provides useful navigation and preserves investigation capability boundaries', () => {
  const html = render()
  assert.match(html, /href="\/infrastructure"/)
  assert.match(html, /href="\/traces"/)
  assert.match(html, /Recommendation is not implemented/)
  assert.match(html, /do not establish causal root cause/)
})
test('endpoint display redacts credentials, queries, fragments and invalid configuration', () => {
  assert.equal(runtime.endpointLabel('https://person:secret@example.test/api?token=secret#key'), 'https://example.test/api')
  assert.equal(runtime.endpointLabel('/api/v1?key=secret'), '/api/v1')
  assert.equal(runtime.endpointLabel('javascript:secret'), 'Endpoint display unavailable')
  assert.equal(runtime.endpointLabel('http://['), 'Endpoint display unavailable')
})
