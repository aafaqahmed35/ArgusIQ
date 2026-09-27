import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import PageHeader from '../components/layout/PageHeader'
import InvestigationToolbar from '../components/traces/InvestigationToolbar'
import TraceDetailsDrawer from '../components/traces/TraceDetailsDrawer'
import TracePanel from '../components/traces/TracePanel'
import { getTraceKey, useTraces } from '../hooks/useTraces'
import { buildTraceSearchRequest, normalizeTraceSearchResult } from './traceExplorerSearch'
import { searchTraces } from '../services/traceApi'
import '../styles/dashboard.css'

const DEFAULT_QUERY = {
  query: '',
  traceId: '',
  spanId: '',
  service: '',
  endpoint: '',
  httpMethod: '',
  status: '',
  latency: '',
  from: '',
  to: '',
  page: 0,
  size: 25,
  sortBy: 'startTime',
  sortDirection: 'desc',
}

const SUPPORTED_SORT_FIELDS = new Set([
  'startTime',
  'durationMs',
  'serviceName',
  'statusCode',
  'httpMethod',
  'requestUri',
  'traceId',
])
const SUPPORTED_PAGE_SIZES = new Set([10, 25, 50, 100])

function parseInteger(value, fallback) {
  const parsed = Number.parseInt(value, 10)
  return Number.isInteger(parsed) ? parsed : fallback
}

function readQuery(searchParams) {
  const page = Math.max(0, parseInteger(searchParams.get('page'), DEFAULT_QUERY.page))
  const requestedSize = parseInteger(searchParams.get('size'), DEFAULT_QUERY.size)
  const size = SUPPORTED_PAGE_SIZES.has(requestedSize) ? requestedSize : DEFAULT_QUERY.size
  const requestedSort = searchParams.get('sortBy')
  const sortBy = SUPPORTED_SORT_FIELDS.has(requestedSort) ? requestedSort : DEFAULT_QUERY.sortBy
  const sortDirection = searchParams.get('sortDirection')?.toLowerCase() === 'asc' ? 'asc' : 'desc'

  return {
    query: searchParams.get('query') ?? '',
    traceId: searchParams.get('traceId') ?? '',
    spanId: searchParams.get('spanId') ?? '',
    service: searchParams.get('service') ?? '',
    endpoint: searchParams.get('endpoint') ?? '',
    httpMethod: searchParams.get('httpMethod') ?? '',
    status: searchParams.get('status') ?? '',
    latency: searchParams.get('latency') ?? '',
    from: searchParams.get('from') ?? '',
    to: searchParams.get('to') ?? '',
    page,
    size,
    sortBy,
    sortDirection,
  }
}

function countActiveFilters(query) {
  return [
    query.query,
    query.traceId,
    query.spanId,
    query.service,
    query.endpoint,
    query.httpMethod,
    query.status,
    query.latency,
    query.from,
    query.to,
  ].filter(Boolean).length
}

function TraceExplorer() {
  const { websocketStatus, liveTraceSequence } = useTraces()
  const [searchParams, setSearchParams] = useSearchParams()
  const query = useMemo(() => readQuery(searchParams), [searchParams])
  const [searchInput, setSearchInput] = useState({ source: query.query, value: query.query })
  const searchDraft = searchInput.source === query.query ? searchInput.value : query.query
  const [result, setResult] = useState({
    items: [],
    page: query.page,
    size: query.size,
    totalItems: 0,
    totalPages: 0,
    hasNext: false,
    hasPrevious: false,
  })
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState(null)
  const [selectedTrace, setSelectedTrace] = useState(null)
  const [refreshSequence, setRefreshSequence] = useState(0)
  const [acknowledgedLiveSequence, setAcknowledgedLiveSequence] = useState(liveTraceSequence)
  const requestSequenceRef = useRef(0)
  const liveTraceSequenceRef = useRef(liveTraceSequence)

  useEffect(() => {
    liveTraceSequenceRef.current = liveTraceSequence
  }, [liveTraceSequence])

  const updateQuery = useCallback(
    (patch, { resetPage = true } = {}) => {
      setSelectedTrace(null)
      setSearchParams(
        (currentParams) => {
          const nextParams = new URLSearchParams(currentParams)

          Object.entries(patch).forEach(([key, value]) => {
            const defaultValue = DEFAULT_QUERY[key]
            if (value === '' || value === null || value === undefined || value === defaultValue) {
              nextParams.delete(key)
            } else {
              nextParams.set(key, String(value))
            }
          })

          if (resetPage && !Object.hasOwn(patch, 'page')) {
            nextParams.delete('page')
          }

          return nextParams
        },
        { replace: true },
      )
    },
    [setSearchParams],
  )

  useEffect(() => {
    if (searchDraft === query.query) {
      return undefined
    }

    const debounceTimer = window.setTimeout(() => {
      updateQuery({ query: searchDraft })
    }, 300)

    return () => window.clearTimeout(debounceTimer)
  }, [query.query, searchDraft, updateQuery])

  const searchRequest = useMemo(
    () => buildTraceSearchRequest(query, refreshSequence, liveTraceSequence),
    [liveTraceSequence, query, refreshSequence],
  )

  useEffect(() => {
    const { criteria: apiCriteria } = searchRequest
    const controller = new AbortController()
    const requestSequence = ++requestSequenceRef.current
    const liveSequenceAtRequestStart = liveTraceSequenceRef.current

    queueMicrotask(() => {
      if (controller.signal.aborted) {
        return
      }

      setIsLoading(true)
      setError(null)
      setResult((currentResult) => ({
        ...currentResult,
        items: [],
        page: apiCriteria.page,
        size: apiCriteria.size,
        totalItems: 0,
        totalPages: 0,
        hasNext: false,
        hasPrevious: apiCriteria.page > 0,
      }))

      searchTraces(apiCriteria, { signal: controller.signal })
        .then((pageResult) => {
          if (requestSequence !== requestSequenceRef.current) {
            return
          }

          const nextResult = normalizeTraceSearchResult(pageResult, apiCriteria)
          setResult(nextResult)
          setSelectedTrace((currentTrace) => {
            if (!currentTrace) return null
            return nextResult.items.some((trace) => getTraceKey(trace) === getTraceKey(currentTrace)) ? currentTrace : null
          })
          setAcknowledgedLiveSequence(liveSequenceAtRequestStart)
        })
        .catch((requestError) => {
          if (controller.signal.aborted || requestSequence !== requestSequenceRef.current) {
            return
          }
          setError(requestError)
        })
        .finally(() => {
          if (requestSequence === requestSequenceRef.current) {
            setIsLoading(false)
          }
        })
    })

    return () => controller.abort()
  }, [searchRequest])

  const clearFilters = useCallback(() => {
    setSearchInput({ source: '', value: '' })
    setSelectedTrace(null)
    setSearchParams({}, { replace: true })
  }, [setSearchParams])

  const refresh = useCallback(() => {
    setRefreshSequence((currentSequence) => currentSequence + 1)
  }, [])

  const hasNewTraces = liveTraceSequence > acknowledgedLiveSequence
  const activeFilterCount = countActiveFilters(query)

  return (
    <div className="trace-explorer">
      <section className="trace-explorer__header" aria-label="Trace Explorer header">
        <PageHeader
          title="Trace Explorer"
          subtitle="Find traces, inspect requests, and understand system behavior with bounded server-side queries."
          websocketStatus={websocketStatus}
          isLoading={isLoading}
          onRefresh={refresh}
        />
      </section>

      <div className="trace-query-area">
        {hasNewTraces ? (
          <div className="trace-live-notice" role="status">
            <span>New trace data is available. The current page has not been changed.</span>
            <button type="button" onClick={refresh}>Refresh results</button>
          </div>
        ) : null}

        <InvestigationToolbar
          query={query}
          searchDraft={searchDraft}
          onSearchDraftChange={(value) => setSearchInput({ source: query.query, value })}
          updateQuery={updateQuery}
          clearFilters={clearFilters}
          activeFilterCount={activeFilterCount}
        />
      </div>

      <section className="trace-explorer__workspace trace-workspace" aria-label="Trace investigation workspace">
        <TracePanel
          traces={result.items}
          isLoading={isLoading}
          error={error}
          onTraceSelect={setSelectedTrace}
          activeFilterCount={activeFilterCount}
          onClearFilters={clearFilters}
          selectedTrace={selectedTrace}
          sourceLabel="Bounded results from GET /api/v1/search/traces"
          emptyTitle="No traces matched"
          emptyMessage="Broaden the investigation criteria or wait for new telemetry."
          pagination={result}
          onPageChange={(page) => updateQuery({ page }, { resetPage: false })}
          onPageSizeChange={(size) => updateQuery({ size, page: 0 }, { resetPage: false })}
        />
        <TraceDetailsDrawer
          trace={selectedTrace}
          onClose={() => setSelectedTrace(null)}
          variant="panel"
        />
      </section>
    </div>
  )
}

export default TraceExplorer
