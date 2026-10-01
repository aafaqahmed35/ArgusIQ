import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import PageHeader from '../components/layout/PageHeader'
import ServiceDetailPanel from '../components/services/ServiceDetailPanel'
import ServiceListPanel from '../components/services/ServiceListPanel'
import { fetchService, fetchServices, fetchServiceRelationships } from '../services/traceApi'
import '../styles/dashboard.css'
import '../styles/evidence.css'

const SORT_FIELD = {
  TRAFFIC: 'traffic',
  LATENCY: 'latency',
}

function Services() {
  const [services, setServices] = useState([])
  const [selectedServiceId, setSelectedServiceId] = useState(null)
  const [selectedService, setSelectedService] = useState(null)
  const [sortField, setSortField] = useState(SORT_FIELD.TRAFFIC)
  const [isLoading, setIsLoading] = useState(true)
  const [isDetailLoading, setIsDetailLoading] = useState(false)
  const [error, setError] = useState(null)
  const [detailError, setDetailError] = useState(null)
  const serviceListRequestId = useRef(0)
  const serviceDetailRequestId = useRef(0)
  const [relationships, setRelationships] = useState(null)
  const [relationshipError, setRelationshipError] = useState(null)
  const [relationshipLoading, setRelationshipLoading] = useState(true)
  const relationshipRequestId = useRef(0)
  const loadRelationships = useCallback(async () => {
    const current = ++relationshipRequestId.current
    setRelationshipLoading(true)
    setRelationshipError(null)
    try {
      const data = await fetchServiceRelationships()
      if (current === relationshipRequestId.current) setRelationships(data)
    } catch (failure) {
      if (current === relationshipRequestId.current) { setRelationships(null); setRelationshipError(failure) }
    } finally {
      if (current === relationshipRequestId.current) setRelationshipLoading(false)
    }
  }, [])

  const loadServices = useCallback(async () => {
    const requestId = ++serviceListRequestId.current
    setIsLoading(true)
    setError(null)
    try {
      const data = await fetchServices()
      if (requestId === serviceListRequestId.current) {
        setServices(Array.isArray(data) ? data : [])
      }
    } catch (requestError) {
      if (requestId === serviceListRequestId.current) {
        setServices([])
        setError(requestError)
      }
    } finally {
      if (requestId === serviceListRequestId.current) {
        setIsLoading(false)
      }
    }
  }, [])

  const loadServiceDetail = useCallback(async (serviceId) => {
    const requestId = ++serviceDetailRequestId.current
    if (serviceId === null) {
      setSelectedService(null)
      setIsDetailLoading(false)
      setDetailError(null)
      return
    }

    setIsDetailLoading(true)
    setDetailError(null)
    setSelectedService(null)
    try {
      const data = await fetchService(serviceId)
      if (requestId === serviceDetailRequestId.current) {
        setSelectedService(data)
      }
    } catch (requestError) {
      if (requestId === serviceDetailRequestId.current) {
        setSelectedService(null)
        setDetailError(requestError)
      }
    } finally {
      if (requestId === serviceDetailRequestId.current) {
        setIsDetailLoading(false)
      }
    }
  }, [])

  useEffect(() => {
    queueMicrotask(loadServices)
    queueMicrotask(loadRelationships)
    return () => {
      serviceListRequestId.current += 1
      serviceDetailRequestId.current += 1
      relationshipRequestId.current += 1
    }
  }, [loadServices, loadRelationships])

  const sortedServices = useMemo(() => {
    const values = [...services]
    if (sortField === SORT_FIELD.LATENCY) {
      return values.sort((left, right) => {
        const leftLatency = left.averageLatencyMs ?? -1
        const rightLatency = right.averageLatencyMs ?? -1
        return rightLatency - leftLatency || left.serviceName.localeCompare(right.serviceName)
      })
    }
    return values.sort(
      (left, right) => right.requestCount - left.requestCount || left.serviceName.localeCompare(right.serviceName),
    )
  }, [services, sortField])

  const handleServiceSelect = useCallback((service) => {
    setSelectedServiceId(service.id)
    loadServiceDetail(service.id)
  }, [loadServiceDetail])

  const handleRefresh = useCallback(async () => {
    await Promise.all([loadServices(), loadServiceDetail(selectedServiceId), loadRelationships()])
  }, [loadServiceDetail, loadServices, selectedServiceId, loadRelationships])

  return (
    <div className="services-workspace evidence-workspace">
      <section className="services-workspace__header" aria-label="Services header">
        <PageHeader
          title="Services"
          subtitle="Discovered OpenTelemetry service identities with observed request and operation metrics."
          isLoading={isLoading || isDetailLoading}
          onRefresh={handleRefresh}
          showConnectionStatus={false}
          statusNote="Persisted service telemetry"
        />
      </section>

      <section className="service-evidence-body" aria-label="Observed services">
        <ServiceListPanel
          services={sortedServices}
          isLoading={isLoading}
          error={error}
          selectedServiceId={selectedServiceId}
          onServiceSelect={handleServiceSelect}
          sortField={sortField}
          onSortFieldChange={setSortField}
        />
        <ServiceDetailPanel
          service={selectedService}
          isLoading={isDetailLoading}
          error={detailError}
          relationships={relationships}
          relationshipError={relationshipError}
          relationshipLoading={relationshipLoading}
        />
      </section>
    </div>
  )
}

export default Services
