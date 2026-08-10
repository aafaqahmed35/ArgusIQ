import React from 'react'

function SpanFilterToolbar({
  searchQuery,
  onSearchChange,
  filterMode,
  onFilterModeChange,
  serviceFilter,
  onServiceFilterChange,
  availableServices = [],
}) {
  return (
    <div
      className="span-filter-toolbar"
      style={{
        display: 'flex',
        alignItems: 'center',
        flexWrap: 'wrap',
        gap: '0.75rem',
        padding: '0.6rem 0.85rem',
        background: '#0A192F',
        border: '1px solid rgba(255, 255, 255, 0.08)',
        borderRadius: '6px',
        fontSize: '0.8rem',
      }}
    >
      {/* Search Input */}
      <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', flexGrow: 1, minWidth: '220px' }}>
        <span style={{ color: '#94A3B8' }}>🔍</span>
        <input
          type="text"
          value={searchQuery}
          onChange={(e) => onSearchChange(e.target.value)}
          placeholder="Filter spans by name, service, span ID, status..."
          style={{
            width: '100%',
            background: 'rgba(15, 23, 42, 0.8)',
            border: '1px solid rgba(255, 255, 255, 0.1)',
            borderRadius: '4px',
            color: '#F5F7FA',
            padding: '4px 8px',
            fontSize: '0.75rem',
            outline: 'none',
          }}
        />
        {searchQuery && (
          <button
            type="button"
            onClick={() => onSearchChange('')}
            style={{ background: 'none', border: 'none', color: '#94A3B8', cursor: 'pointer', fontSize: '0.75rem' }}
          >
            ✕
          </button>
        )}
      </div>

      {/* Service Dropdown Filter */}
      {availableServices.length > 1 && (
        <select
          value={serviceFilter}
          onChange={(e) => onServiceFilterChange(e.target.value)}
          style={{
            background: 'rgba(15, 23, 42, 0.8)',
            border: '1px solid rgba(255, 255, 255, 0.1)',
            borderRadius: '4px',
            color: '#F5F7FA',
            padding: '4px 8px',
            fontSize: '0.75rem',
            outline: 'none',
          }}
        >
          <option value="all">All Services ({availableServices.length})</option>
          {availableServices.map((svc) => (
            <option key={svc} value={svc}>
              {svc}
            </option>
          ))}
        </select>
      )}

      {/* Quick Mode Toggles */}
      <div style={{ display: 'flex', gap: '0.25rem' }}>
        {[
          { id: 'all', label: 'All Spans' },
          { id: 'errors', label: 'Errors Only 🔴' },
          { id: 'critical', label: 'Critical Path ⚡' },
          { id: 'db', label: 'Database' },
          { id: 'http', label: 'HTTP' },
        ].map((btn) => (
          <button
            key={btn.id}
            type="button"
            onClick={() => onFilterModeChange(btn.id)}
            style={{
              background: filterMode === btn.id ? 'rgba(56, 189, 248, 0.2)' : 'rgba(255, 255, 255, 0.04)',
              border: `1px solid ${filterMode === btn.id ? '#38bdf8' : 'rgba(255, 255, 255, 0.08)'}`,
              color: filterMode === btn.id ? '#38bdf8' : '#94A3B8',
              borderRadius: '4px',
              padding: '3px 8px',
              fontSize: '0.7rem',
              fontWeight: filterMode === btn.id ? 700 : 500,
              cursor: 'pointer',
            }}
          >
            {btn.label}
          </button>
        ))}
      </div>
    </div>
  )
}

export default SpanFilterToolbar
