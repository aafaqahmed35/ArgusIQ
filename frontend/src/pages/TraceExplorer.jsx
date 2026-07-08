import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import PageHeader from '../components/layout/PageHeader'
import InvestigationToolbar from '../components/traces/InvestigationToolbar'
import TraceDetailsDrawer from '../components/traces/TraceDetailsDrawer'
import TracePanel from '../components/traces/TracePanel'
import { useTraceFilters } from '../hooks/useTraceFilters'
import { getTraceKey, useTraces } from '../hooks/useTraces'
import { fetchSlowTraces } from '../services/traceApi'
import '../styles/dashboard.css'

const INVESTIGATION_MODE = {
  ALL: 'all',
  SLOW: 'slow',
}

const HIGHLIGHT_DURATION_MS = 2000

function dedupeTraces(traces) {
  const seenTraceKeys = new Set()

  return traces.filter((trace) => {
    const traceKey = getTraceKey(trace)

    if (seenTraceKeys.has(traceKey)) {
      return false
    }

    seenTraceKeys.add(traceKey)
    return true
  })
}

function TraceExplorer() {
  const { traces, isLoading, error, websocketStatus, refreshTraces } = useTraces()
  const [investigationMode, setInvestigationMode] = useState(INVESTIGATION_MODE.ALL)
  const [slowTraces, setSlowTraces] = useState([])
  const [isSlowLoading, setIsSlowLoading] = useState(false)
  const [slowError, setSlowError] = useState(null)
  const [selectedTrace, setSelectedTrace] = useState(null)
  const [highlightedTraceKeys, setHighlightedTraceKeys] = useState([])
  const previousTraceKeysRef = useRef(new Set())

  const activeTraces = investigationMode === INVESTIGATION_MODE.ALL ? traces : slowTraces
  const activeIsLoading = investigationMode === INVESTIGATION_MODE.ALL ? isLoading : isSlowLoading
  const activeError = investigationMode === INVESTIGATION_MODE.ALL ? error : slowError

  const { filteredTraces, filters, updateFilter, clearFilters, activeFilterCount } = useTraceFilters(activeTraces)

  const loadSlowTraces = useCallback(async () => {
    setIsSlowLoading(true)
    setSlowError(null)

    try {
      const data = await fetchSlowTraces()
      setSlowTraces(Array.isArray(data) ? dedupeTraces(data) : [])
    } catch (requestError) {
      setSlowError(requestError)
    } finally {
      setIsSlowLoading(false)
    }
  }, [])

  useEffect(() => {
    if (investigationMode === INVESTIGATION_MODE.SLOW) {
      queueMicrotask(loadSlowTraces)
    }
  }, [investigationMode, loadSlowTraces])

  useEffect(() => {
    if (investigationMode !== INVESTIGATION_MODE.ALL) {
      previousTraceKeysRef.current = new Set(traces.map(getTraceKey))
      return undefined
    }

    const currentTraceKeys = traces.map(getTraceKey)
    const currentTraceKeySet = new Set(currentTraceKeys)
    const newTraceKeys = currentTraceKeys.filter((traceKey) => !previousTraceKeysRef.current.has(traceKey))

    if (previousTraceKeysRef.current.size > 0 && newTraceKeys.length > 0) {
      setHighlightedTraceKeys(newTraceKeys)

      const timeoutId = window.setTimeout(() => {
        setHighlightedTraceKeys([])
      }, HIGHLIGHT_DURATION_MS)

      previousTraceKeysRef.current = currentTraceKeySet

      return () => {
        window.clearTimeout(timeoutId)
      }
    }

    previousTraceKeysRef.current = currentTraceKeySet
    return undefined
  }, [investigationMode, traces])

  const visibleSelectedTrace = useMemo(() => {
    if (!selectedTrace) {
      return null
    }

    return filteredTraces.some((trace) => getTraceKey(trace) === getTraceKey(selectedTrace)) ? selectedTrace : null
  }, [filteredTraces, selectedTrace])

  const handleRefresh = useCallback(async () => {
    if (investigationMode === INVESTIGATION_MODE.ALL) {
      await refreshTraces()
      return
    }

    await loadSlowTraces()
  }, [investigationMode, loadSlowTraces, refreshTraces])

  const handleInvestigationModeChange = useCallback((nextMode) => {
    setInvestigationMode(nextMode)
    setSelectedTrace(null)
  }, [])

  const sourceLabel =
    investigationMode === INVESTIGATION_MODE.SLOW
      ? 'Showing slow traces from GET /api/v1/traces/slow'
      : 'Showing all loaded traces from GET /api/v1/traces'

  const emptyTitle =
    investigationMode === INVESTIGATION_MODE.SLOW ? 'No slow traces returned' : 'No traces available'

  const emptyMessage =
    investigationMode === INVESTIGATION_MODE.SLOW
      ? 'The slow traces endpoint did not return any records.'
      : 'Trace records will appear here as soon as the frontend receives telemetry.'

  return (
    <div className="trace-explorer">
      <section className="trace-explorer__header" aria-label="Trace Explorer header">
        <PageHeader
          title="Trace Explorer"
          subtitle="Find traces, inspect requests, and understand system behavior in real time."
          websocketStatus={websocketStatus}
          isLoading={activeIsLoading}
          onRefresh={handleRefresh}
        />
      </section>

      <InvestigationToolbar
        filters={filters}
        updateFilter={updateFilter}
        clearFilters={clearFilters}
        activeFilterCount={activeFilterCount}
        investigationMode={investigationMode}
        onInvestigationModeChange={handleInvestigationModeChange}
      />

      <section className="trace-explorer__workspace trace-workspace" aria-label="Trace investigation workspace">
        <TracePanel
          traces={filteredTraces}
          isLoading={activeIsLoading}
          error={activeError}
          onTraceSelect={setSelectedTrace}
          activeFilterCount={activeFilterCount}
          onClearFilters={clearFilters}
          selectedTrace={visibleSelectedTrace}
          highlightedTraceKeys={highlightedTraceKeys}
          sourceLabel={sourceLabel}
          emptyTitle={emptyTitle}
          emptyMessage={emptyMessage}
        />
        <TraceDetailsDrawer
          trace={visibleSelectedTrace}
          onClose={() => setSelectedTrace(null)}
          variant="panel"
        />
      </section>
    </div>
  )
}

export default TraceExplorer
