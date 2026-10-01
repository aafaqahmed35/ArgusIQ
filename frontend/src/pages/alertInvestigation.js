import { buildTraceSearchHref } from './overviewData.js'

export function occurrenceState(alert) {
  if (alert.status === 'RESOLVED') return 'Resolved'
  if (alert.status === 'ACKNOWLEDGED' || (alert.status === 'OPEN' && alert.acknowledged === true)) return 'Acknowledged · active'
  if (alert.status === 'OPEN') return 'Open · active'
  return 'Occurrence state unavailable'
}

export function partitionOccurrences(alerts) {
  return {
    active: alerts.filter((a) => a.status === 'OPEN' || a.status === 'ACKNOWLEDGED'),
    resolved: alerts.filter((a) => a.status === 'RESOLVED'),
    unknown: alerts.filter((a) => !['OPEN', 'ACKNOWLEDGED', 'RESOLVED'].includes(a.status)),
  }
}

export function evidenceValue(value, unit) {
  if (typeof value !== 'number' || !Number.isFinite(value)) return '—'
  return `${value.toLocaleString(undefined, { maximumFractionDigits: 3 })}${unit === 'PERCENT' ? '%' : unit === 'MILLISECONDS' ? ' ms' : ''}`
}

export function ruleScope(rule) {
  if (!rule || rule.serviceName === undefined) return 'Scope unavailable'
  return rule.serviceName === null ? 'All services' : rule.serviceName || 'Scope unavailable'
}

export function investigationLinks(alert) {
  const evidence = alert.evidence ?? {}
  const traceId = alert.relatedTrace || evidence.traceId
  const spanId = alert.relatedSpan || evidence.spanId
  const service = alert.relatedService || evidence.serviceName
  const links = []
  if (traceId) links.push({ label: 'Inspect trace', href: buildTraceSearchHref({ traceId }) })
  if (spanId) links.push({ label: 'Find containing trace by span ID', href: buildTraceSearchHref({ spanId, ...(traceId ? { traceId } : {}) }) })
  if (service) {
    links.push({ label: 'Investigate entry-service traces', href: buildTraceSearchHref({ service }) })
    links.push({ label: 'Open service inventory', href: '/services' })
  }
  if (evidence.operationName) links.push({ label: 'Search trace text for operation', href: buildTraceSearchHref({ query: evidence.operationName }) })
  return links
}

export function buildRuleRequest(draft) {
  const name = draft.name.trim()
  const serviceName = draft.serviceName.trim() || null
  if (!name || name.length > 255 || (serviceName && serviceName.length > 255)) throw new Error('Name and service scope must be at most 255 characters; name is required.')
  if (!['TRACE_ERROR', 'ERROR_RATE_THRESHOLD', 'P95_LATENCY_THRESHOLD'].includes(draft.type)) throw new Error('Unsupported rule type.')
  if (!['INFO', 'WARNING', 'CRITICAL'].includes(draft.severity)) throw new Error('Unsupported configured severity.')
  const request = { name, serviceName, type: draft.type, severity: draft.severity, enabled: draft.enabled }
  if (draft.type !== 'TRACE_ERROR') {
    const threshold = draft.threshold.trim() === '' ? NaN : Number(draft.threshold)
    const windowSeconds = Number(draft.windowSeconds)
    const minimumSamples = Number(draft.minimumSamples)
    if (!Number.isFinite(threshold) || threshold < 0 || (draft.type === 'ERROR_RATE_THRESHOLD' && threshold > 100)) throw new Error('Enter a valid non-negative threshold (error rate: 0–100%).')
    if (!Number.isInteger(windowSeconds) || windowSeconds < 1 || windowSeconds > 31536000) throw new Error('Window must be 1–31536000 whole seconds.')
    if (!Number.isInteger(minimumSamples) || minimumSamples < 1 || minimumSamples > 1000000000) throw new Error('Minimum samples must be 1–1000000000.')
    Object.assign(request, { threshold, windowSeconds, minimumSamples, comparator: 'GREATER_THAN_OR_EQUAL' })
  }
  return request
}

// The same state controller is exercised by focused tests and the React page.
// REST snapshots and mutation responses are authoritative; connection state is not an input.
export function createAlertInvestigationStore(api) {
  let state = { alerts: null, rules: null, occurrenceLoading: true, ruleLoading: true, occurrenceError: null, ruleError: null, actionError: null, createError: null, busy: null, notice: null }
  const listeners = new Set()
  let occurrenceRevision = 0
  let ruleRevision = 0
  const update = (patch) => { state = { ...state, ...patch }; listeners.forEach((listener) => listener()) }
  async function loadOccurrences() {
    const revision = ++occurrenceRevision
    update({ occurrenceLoading: true, occurrenceError: null })
    try {
      const alerts = await api.fetchAlerts()
      if (!Array.isArray(alerts)) throw new Error('Invalid occurrence response')
      if (revision === occurrenceRevision) update({ alerts })
    } catch (error) {
      if (revision === occurrenceRevision) update({ occurrenceError: error })
    } finally {
      if (revision === occurrenceRevision) update({ occurrenceLoading: false })
    }
  }
  async function loadRules() {
    const revision = ++ruleRevision
    update({ ruleLoading: true, ruleError: null })
    try {
      const rules = await api.fetchAlertRules()
      if (!Array.isArray(rules)) throw new Error('Invalid rule response')
      if (revision === ruleRevision) update({ rules })
    } catch (error) {
      if (revision === ruleRevision) update({ ruleError: error })
    } finally {
      if (revision === ruleRevision) update({ ruleLoading: false })
    }
  }
  return {
    getSnapshot: () => state,
    subscribe: (listener) => { listeners.add(listener); return () => listeners.delete(listener) },
    refresh: () => state.busy ? Promise.resolve() : Promise.all([loadOccurrences(), loadRules()]),
    dispose: () => { occurrenceRevision++; ruleRevision++ },
    async mutate(kind, alertId) {
      if (state.busy || alertId === null || alertId === undefined) return
      occurrenceRevision++ // A pre-mutation GET must never overwrite a successful mutation.
      update({ busy: { kind, alertId }, actionError: null, notice: null, occurrenceLoading: false })
      try {
        const action = kind === 'acknowledge' ? api.acknowledgeAlert : kind === 'resolve' ? api.resolveAlert : null
        if (!action) throw new Error('Unsupported action')
        const returned = await action(alertId)
        if (returned?.alertId !== alertId || !['OPEN', 'ACKNOWLEDGED', 'RESOLVED'].includes(returned.status)) throw new Error('Invalid mutation response; refresh to verify persisted state.')
        update({ alerts: (state.alerts ?? []).map((alert) => alert.alertId === alertId ? returned : alert), notice: `Backend returned ${occurrenceState(returned)} for occurrence ${alertId}.` })
        await loadOccurrences()
      } catch (error) {
        update({ actionError: error })
      } finally {
        update({ busy: null })
      }
    },
    async createRule(request) {
      if (state.busy) return false
      update({ busy: { kind: 'create' }, createError: null, notice: null })
      try {
        const returned = await api.createAlertRule(request)
        if (returned?.id === null || returned?.id === undefined) throw new Error('Invalid rule response; refresh to verify persisted state.')
        ruleRevision++
        update({ rules: [...(state.rules ?? []).filter((rule) => rule.id !== returned.id), returned], notice: 'Rule created. Evaluation occurs on subsequent telemetry commits.' })
        await loadRules()
        return true
      } catch (error) {
        update({ createError: error })
        return false
      } finally {
        update({ busy: null })
      }
    },
  }
}
