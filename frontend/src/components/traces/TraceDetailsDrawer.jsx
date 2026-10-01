import { useEffect, useRef, useState, useMemo } from 'react'
import { Link } from 'react-router-dom'
import { fetchTraceByTraceId } from '../../services/traceApi'
import { buildSpanTree } from '../../lib/spanTreeBuilder'
import { computeCriticalPath } from '../../lib/criticalPath'
import { parseBackendUtcTimestamp } from '../../lib/backendDateTime'
import TraceDetailSection from './TraceDetailSection'
import TraceMetadataGrid from './TraceMetadataGrid'
import SpanTree from './SpanTree'
import SpanWaterfall from './SpanWaterfall'
import CriticalPathBanner from './CriticalPathBanner'
import InvestigationFindings from './InvestigationFindings'
import SpanInspector from './SpanInspector'
import InvestigationTimeline from './InvestigationTimeline'
import MiniTraceMap from './MiniTraceMap'
import ServiceCallStats from './ServiceCallStats'
import SpanFilterToolbar from './SpanFilterToolbar'

function formatDate(value) {
  if (!value || value === '—') return '—'
  const date = parseBackendUtcTimestamp(value)
  if (!date) return String(value)
  return new Intl.DateTimeFormat('en', { dateStyle: 'medium', timeStyle: 'medium' }).format(date)
}

function formatDuration(value) {
  if (value === '—' || value === null || value === undefined) return '—'
  const numericValue = Number(value)
  if (Number.isNaN(numericValue)) return String(value)
  return `${numericValue.toLocaleString()} ms`
}

function getStatusBadge(status) {
  const statusStr = String(status || '').toUpperCase()
  const numericStatus = Number(status)

  if (statusStr === 'ERROR' || (!Number.isNaN(numericStatus) && numericStatus >= 500)) {
    return { label: 'ERROR', className: 'status-pill status-pill--error' }
  }

  if (statusStr === 'UNSET' || (!Number.isNaN(numericStatus) && numericStatus >= 400 && numericStatus < 500)) {
    return { label: statusStr || String(numericStatus), className: 'status-pill status-pill--warning' }
  }

  if (statusStr === 'OK' || (!Number.isNaN(numericStatus) && numericStatus >= 200 && numericStatus < 400)) {
    return { label: statusStr || String(numericStatus), className: 'status-pill status-pill--success' }
  }

  return { label: statusStr || 'UNKNOWN', className: 'status-pill status-pill--neutral' }
}

const TABS = [
  { id: 'overview', label: 'Overview' },
  { id: 'waterfall', label: 'Waterfall' },
  { id: 'tree', label: 'Trace Tree' },
  { id: 'critical', label: 'Critical Path' },
  { id: 'timeline', label: 'Timeline' },
  { id: 'topology', label: 'Topology' },
  { id: 'services', label: 'Services' },
  { id: 'inspector', label: 'Span Inspector' },
]

function TraceDetailsDrawer({ trace, onClose, variant = 'drawer' }) {
  const drawerPanelRef = useRef(null)
  const isOpen = Boolean(trace)
  const isPanel = variant === 'panel'

  const [activeTab, setActiveTab] = useState('overview')
  const [fullTraceDetail, setFullTraceDetail] = useState(null)
  const [isLoadingDetail, setIsLoadingDetail] = useState(false)
  const [detailError, setDetailError] = useState(false)
  const [selectedSpan, setSelectedSpan] = useState(null)

  // Filter toolbar state inside drawer
  const [searchQuery, setSearchQuery] = useState('')
  const [filterMode, setFilterMode] = useState('all')
  const [serviceFilter, setServiceFilter] = useState('all')

  // Fetch full trace details when trace is selected
  useEffect(() => {
    if (!trace) return undefined

    const traceId = trace.traceId || trace.id

    if (!traceId) return undefined

    let isSubscribed = true
    const fetchTimer = window.setTimeout(() => {
      setIsLoadingDetail(true)
      setDetailError(false)
      fetchTraceByTraceId(traceId)
        .then((detail) => {
          if (isSubscribed) {
            setFullTraceDetail({ traceId, detail: detail || null })
          }
        })
        .catch(() => {
          if (isSubscribed) {
            setFullTraceDetail({ traceId, detail: null })
            setDetailError(true)
          }
        })
        .finally(() => {
          if (isSubscribed) {
            setIsLoadingDetail(false)
          }
        })
    }, 0)

    return () => {
      isSubscribed = false
      window.clearTimeout(fetchTimer)
    }
  }, [trace])

  // Handle backdrop click to close
  useEffect(() => {
    if (!isOpen || isPanel) return undefined

    function handleDocumentClick(event) {
      const target = event.target
      if (drawerPanelRef.current?.contains(target)) return
      if (target.closest?.('.trace-table__inspect')) return
      onClose()
    }

    document.addEventListener('click', handleDocumentClick)
    return () => document.removeEventListener('click', handleDocumentClick)
  }, [isOpen, isPanel, onClose])

  useEffect(() => {
    if (!isOpen || isPanel) return undefined

    function handleEscape(event) {
      if (event.key === 'Escape') onClose()
    }

    document.addEventListener('keydown', handleEscape)
    return () => document.removeEventListener('keydown', handleEscape)
  }, [isOpen, isPanel, onClose])

  // Aggregate telemetry models
  const selectedTraceId = trace?.traceId || trace?.id
  const effectiveFullTraceDetail =
    selectedTraceId && fullTraceDetail?.traceId === selectedTraceId ? fullTraceDetail.detail : null
  const summary = useMemo(() => effectiveFullTraceDetail?.summary || trace || {}, [effectiveFullTraceDetail, trace])
  const spans = useMemo(
    () => effectiveFullTraceDetail?.spans || (trace?.spans ? trace.spans : []),
    [effectiveFullTraceDetail, trace]
  )

  const traceId = summary.traceId || summary.id || trace?.traceId || trace?.id
  const serviceName = summary.serviceName || trace?.serviceName || 'Unknown service'
  const rootOperation = summary.rootSpanName || trace?.rootSpanName || summary.requestUri || trace?.requestUri || 'Unknown operation'
  const method = summary.httpMethod || trace?.httpMethod || '—'
  const uri = summary.requestUri || trace?.requestUri || '—'
  const durationMs = summary.durationMs ?? summary.executionTimeMs ?? trace?.durationMs ?? trace?.executionTimeMs ?? null
  const statusCode = summary.statusCode || trace?.statusCode || 'UNKNOWN'
  const statusMessage = summary.statusMessage || trace?.statusMessage
  const startTime = summary.startTime || summary.timestamp || trace?.startTime || trace?.timestamp
  const endTime = summary.endTime || trace?.endTime
  const spanCount = summary.spanCount ?? (spans.length || null)

  // Construct Tree & Critical Path
  const treeData = useMemo(() => buildSpanTree(spans, summary), [spans, summary])
  const criticalPathInfo = useMemo(
    () => computeCriticalPath(treeData, effectiveFullTraceDetail?.criticalPath),
    [treeData, effectiveFullTraceDetail]
  )

  const availableServices = useMemo(() => {
    const set = new Set(spans.map((s) => s.serviceName || serviceName))
    return Array.from(set)
  }, [spans, serviceName])

  // Filtered spans for visualization views
  const filteredSpans = useMemo(() => {
    return spans.filter((span) => {
      const sName = span.serviceName || serviceName
      const sStatus = (span.statusCode || '').toUpperCase()

      if (serviceFilter !== 'all' && sName !== serviceFilter) return false
      if (filterMode === 'errors' && sStatus !== 'ERROR') return false
      if (filterMode === 'critical' && !criticalPathInfo.criticalPathSpanIds.has(span.spanId)) return false
      if (filterMode === 'db' && span.kind !== 'INTERNAL' && !span.name?.toLowerCase().includes('select')) return false
      if (filterMode === 'http' && span.kind !== 'SERVER' && span.kind !== 'CLIENT') return false

      return true
    })
  }, [spans, serviceName, serviceFilter, filterMode, criticalPathInfo.criticalPathSpanIds])

  // Filtered tree data
  const filteredTreeData = useMemo(
    () => buildSpanTree(filteredSpans, summary),
    [filteredSpans, summary]
  )

  const badge = getStatusBadge(statusCode)

  const handleSelectSpanAndSwitchTab = (spanNode) => {
    setSelectedSpan(spanNode)
    setActiveTab('inspector')
  }

  // Render tab content
  const renderTabContent = () => {
    if (isLoadingDetail) {
      return (
        <div className="table-state table-state--skeleton trace-detail-state" aria-busy="true" role="status">
          <span className="skeleton-line skeleton-line--wide" />
          <span className="skeleton-line" />
          <span className="skeleton-line skeleton-line--wide" />
        </div>
      )
    }

    if (detailError) {
      return (
        <div className="table-state table-state--error trace-detail-state" role="alert">
          Trace summary remains available, but detailed span evidence could not be loaded from the backend.
        </div>
      )
    }

    switch (activeTab) {
      case 'overview':
        return (
          <div className="trace-detail-stack">
            <InvestigationFindings explanation={effectiveFullTraceDetail?.explanation} />
            <CriticalPathBanner criticalPathInfo={criticalPathInfo} />
            <TraceDetailSection title="Request Information">
              <TraceMetadataGrid
                items={[
                  { label: 'Trace ID', value: <code>{traceId}</code> },
                  { label: 'Status', value: <span className={badge.className}>{badge.label}</span> },
                  { label: 'Service', value: <Link to="/services" className="trace-detail-link">{serviceName} →</Link> },
                  { label: 'Root Operation', value: <strong>{rootOperation}</strong> },
                  { label: 'HTTP Method', value: <span className="method-pill">{method}</span> },
                  { label: 'Request URI', value: <code>{uri}</code> },
                  { label: 'Duration', value: formatDuration(durationMs) },
                  { label: 'Start Time', value: formatDate(startTime) },
                  { label: 'End Time', value: formatDate(endTime) },
                  { label: 'Total Spans', value: spanCount === null ? '—' : `${spanCount} span(s)` },
                  { label: 'Status Message', value: statusMessage || 'None' },
                ]}
              />
            </TraceDetailSection>
          </div>
        )

      case 'waterfall':
        return (
          <div className="trace-detail-stack trace-detail-stack--compact">
            <SpanFilterToolbar
              searchQuery={searchQuery}
              onSearchChange={setSearchQuery}
              filterMode={filterMode}
              onFilterModeChange={setFilterMode}
              serviceFilter={serviceFilter}
              onServiceFilterChange={setServiceFilter}
              availableServices={availableServices}
            />
            <SpanWaterfall
              treeData={filteredTreeData}
              selectedSpanId={selectedSpan?.spanId}
              criticalPathSpanIds={criticalPathInfo.criticalPathSpanIds}
              onSelectSpan={handleSelectSpanAndSwitchTab}
            />
          </div>
        )

      case 'tree':
        return (
          <div className="trace-detail-stack trace-detail-stack--compact">
            <SpanFilterToolbar
              searchQuery={searchQuery}
              onSearchChange={setSearchQuery}
              filterMode={filterMode}
              onFilterModeChange={setFilterMode}
              serviceFilter={serviceFilter}
              onServiceFilterChange={setServiceFilter}
              availableServices={availableServices}
            />
            <SpanTree
              treeData={filteredTreeData}
              selectedSpanId={selectedSpan?.spanId}
              criticalPathSpanIds={criticalPathInfo.criticalPathSpanIds}
              searchQuery={searchQuery}
              onSelectSpan={handleSelectSpanAndSwitchTab}
            />
          </div>
        )

      case 'critical':
        return (
          <div className="trace-detail-stack">
            <CriticalPathBanner criticalPathInfo={criticalPathInfo} />
            <SpanTree
              treeData={buildSpanTree(criticalPathInfo.criticalPathNodes, summary)}
              selectedSpanId={selectedSpan?.spanId}
              criticalPathSpanIds={criticalPathInfo.criticalPathSpanIds}
              onSelectSpan={handleSelectSpanAndSwitchTab}
            />
          </div>
        )

      case 'timeline':
        return (
          <div className="trace-detail-tab-content">
            <InvestigationTimeline
              spans={filteredSpans}
              traceSummary={summary}
              onSelectSpan={handleSelectSpanAndSwitchTab}
            />
          </div>
        )

      case 'topology':
        return (
          <div className="trace-detail-tab-content">
            <MiniTraceMap spans={spans} traceSummary={summary} />
          </div>
        )

      case 'services':
        return (
          <div className="trace-detail-tab-content">
            <ServiceCallStats spans={spans} totalTraceDurationMs={durationMs} />
          </div>
        )

      case 'inspector':
        return (
          <div className="trace-detail-tab-content">
            <SpanInspector selectedSpan={selectedSpan} spanMap={treeData.spanMap} />
          </div>
        )

      default:
        return null
    }
  }

  const drawerTitle = trace ? rootOperation : 'Trace Details'

  const header = (
    <div className="trace-drawer__header operations-center__header">
      <div>
        <p className="section-kicker">Selected trace</p>
        <h2 id="trace-drawer-title" className="trace-drawer__title">
          <span>{drawerTitle}</span>
        </h2>
        <div className="trace-drawer__summary" aria-label="Selected trace summary">
          <span>{serviceName}</span>
          <span>{formatDuration(durationMs)}</span>
          <span className={badge.className}>{badge.label}</span>
          <span>{spanCount === null ? 'Span count unavailable' : `${spanCount} spans`}</span>
        </div>
      </div>
      <button
        className="trace-drawer__close"
        type="button"
        aria-label="Close trace details"
        disabled={!isOpen}
        onClick={onClose}
      >
        Close
      </button>
    </div>
  )

  const tabsNav = (
    <div className="drawer-tabs-nav" role="tablist" aria-label="Trace investigation views">
      {TABS.map((tab) => (
        <button
          key={tab.id}
          type="button"
          onClick={() => setActiveTab(tab.id)}
          className={activeTab === tab.id ? 'is-active' : ''}
          role="tab"
          aria-selected={activeTab === tab.id}
          aria-controls={`trace-tab-${tab.id}`}
        >
          {tab.label}
        </button>
      ))}
    </div>
  )

  if (!trace && !isOpen) {
    return null
  }

  if (isPanel) {
    return (
      <aside className="trace-drawer trace-drawer--panel trace-drawer--open" aria-label="Trace investigation workspace">
        <div className="trace-drawer__panel" ref={drawerPanelRef}>
          {header}
          {tabsNav}
          <div className="trace-drawer-content" role="tabpanel" id={`trace-tab-${activeTab}`}>
            {renderTabContent()}
          </div>
        </div>
      </aside>
    )
  }

  return (
    <div className={`trace-drawer ${isOpen ? 'trace-drawer--open' : ''}`} aria-hidden={!isOpen}>
      <div className="trace-drawer__backdrop" aria-hidden="true" />
      <aside
        className="trace-drawer__panel"
        ref={drawerPanelRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby="trace-drawer-title"
      >
        {header}
        {tabsNav}
        <div className="trace-drawer-content" role="tabpanel" id={`trace-tab-${activeTab}`}>
          {renderTabContent()}
        </div>
      </aside>
    </div>
  )
}

export default TraceDetailsDrawer
