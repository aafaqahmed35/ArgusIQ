import assert from 'node:assert/strict'
import test from 'node:test'
import { classifyTraceObservation, observedTelemetryStatus } from './observedTelemetry.js'

test('string ERROR observations are counted without numeric coercion', () => {
  assert.equal(classifyTraceObservation({ statusCode: 'ERROR' }).kind, 'TRACE_ERROR')
  assert.equal(classifyTraceObservation({ status: 'ERROR', httpStatus: 503 }).kind, 'SERVER_ERROR')
})

test('OK and UNSET remain distinct observed states', () => {
  assert.equal(classifyTraceObservation({ statusCode: 'OK' }).kind, 'OK')
  assert.equal(classifyTraceObservation({ statusCode: 'UNSET' }).kind, 'UNSET')
  assert.equal(classifyTraceObservation({ statusCode: 'OK', exitStatus: '404' }).kind, 'CLIENT_ERROR')
})

test('no data never becomes a healthy claim and connectivity is not an input', () => {
  assert.equal(observedTelemetryStatus({ isLoading: false, observationCount: 0, errorCount: 0, latencyObserved: false }), 'No telemetry')
  assert.equal(observedTelemetryStatus({ isLoading: false, observationCount: 2, errorCount: 0, latencyObserved: false }), 'Observed without errors')
})
