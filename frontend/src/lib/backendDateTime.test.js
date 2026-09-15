import assert from 'node:assert/strict'
import test from 'node:test'
import {
  backendUtcEpochMillis,
  normalizeBackendUtcTimestamp,
  parseBackendUtcTimestamp,
} from './backendDateTime.js'

test('zone-less backend LocalDateTime values are interpreted as UTC', () => {
  assert.equal(normalizeBackendUtcTimestamp('2026-09-15T17:00:00'), '2026-09-15T17:00:00Z')
  assert.equal(parseBackendUtcTimestamp('2026-09-15T17:00:00').toISOString(), '2026-09-15T17:00:00.000Z')
})

test('explicit offsets and Z suffixes are preserved', () => {
  assert.equal(normalizeBackendUtcTimestamp('2026-09-15T17:00:00Z'), '2026-09-15T17:00:00Z')
  assert.equal(normalizeBackendUtcTimestamp('2026-09-15T17:00:00+05:30'), '2026-09-15T17:00:00+05:30')
  assert.equal(parseBackendUtcTimestamp('2026-09-15T17:00:00+05:30').toISOString(), '2026-09-15T11:30:00.000Z')
})

test('null and invalid timestamp values are safe', () => {
  assert.equal(parseBackendUtcTimestamp(null), null)
  assert.equal(parseBackendUtcTimestamp('not-a-timestamp'), null)
  assert.equal(backendUtcEpochMillis('not-a-timestamp'), null)
})
