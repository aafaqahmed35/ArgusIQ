export function buildApiCriteria(query) {
  const criteria = {
    query: query.query.trim(),
    traceId: query.traceId.trim(),
    spanId: query.spanId.trim(),
    serviceExact: query.service.trim(),
    endpoint: query.endpoint.trim(),
    httpMethod: query.httpMethod,
    status: query.status,
    from: query.from,
    to: query.to,
    page: query.page,
    size: query.size,
    sortBy: query.sortBy,
    sortDirection: query.sortDirection,
  }

  if (query.latency === 'fast') {
    criteria.maxDuration = 99
  } else if (query.latency === 'normal') {
    criteria.minDuration = 100
    criteria.maxDuration = 499
  } else if (query.latency === 'slow') {
    criteria.minDuration = 500
    criteria.maxDuration = 999
  } else if (query.latency === 'very-slow') {
    criteria.minDuration = 1000
  }

  return criteria
}

export function buildTraceSearchRequest(query, manualRefreshSequence, liveTraceSequence) {
  return {
    criteria: buildApiCriteria(query),
    revision: `${manualRefreshSequence}:${liveTraceSequence}`,
  }
}

export function normalizeTraceSearchResult(pageResult, criteria) {
  const items = Array.isArray(pageResult?.items) ? pageResult.items : []

  return {
    items,
    page: Number(pageResult?.page ?? criteria.page),
    size: Number(pageResult?.size ?? criteria.size),
    totalItems: Number(pageResult?.totalItems ?? 0),
    totalPages: Number(pageResult?.totalPages ?? 0),
    hasNext: Boolean(pageResult?.hasNext),
    hasPrevious: Boolean(pageResult?.hasPrevious),
  }
}
