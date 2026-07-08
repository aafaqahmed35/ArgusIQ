import { useCallback, useMemo, useState } from 'react'
import PageHeader from '../components/layout/PageHeader'
import ServiceGroupDetail from '../components/services/ServiceGroupDetail'
import ServiceGroupsPanel from '../components/services/ServiceGroupsPanel'
import ServicesLimitationBanner from '../components/services/ServicesLimitationBanner'
import { buildServiceGroups } from '../lib/serviceGrouping'
import { useTraces } from '../hooks/useTraces'
import '../styles/dashboard.css'

const SORT_FIELD = {
  TRAFFIC: 'traffic',
  LATENCY: 'latency',
}

function Services() {
  const { traces, isLoading, error, refreshTraces } = useTraces()
  const [selectedGroup, setSelectedGroup] = useState(null)
  const [sortField, setSortField] = useState(SORT_FIELD.TRAFFIC)

  const serviceGroups = useMemo(() => buildServiceGroups(traces), [traces])

  const sortedGroups = useMemo(() => {
    const groups = [...serviceGroups.groups]

    if (sortField === SORT_FIELD.LATENCY) {
      return groups.sort((left, right) => {
        const leftLatency = left.averageResponseTime ?? -1
        const rightLatency = right.averageResponseTime ?? -1

        return rightLatency - leftLatency || right.requestCount - left.requestCount || left.name.localeCompare(right.name)
      })
    }

    return groups.sort(
      (left, right) =>
        right.requestCount - left.requestCount || left.name.localeCompare(right.name),
    )
  }, [serviceGroups.groups, sortField])

  const handleRefresh = useCallback(async () => {
    await refreshTraces()
  }, [refreshTraces])

  const visibleSelectedGroup = useMemo(() => {
    if (!selectedGroup) {
      return null
    }

    return sortedGroups.some((group) => group.key === selectedGroup.key) ? selectedGroup : null
  }, [selectedGroup, sortedGroups])

  return (
    <div className="services-workspace">
      <section className="services-workspace__header" aria-label="Services header">
        <PageHeader
          title="Services"
          subtitle="Derived from URI prefixes"
          isLoading={isLoading}
          onRefresh={handleRefresh}
          showConnectionStatus={false}
          statusNote="Snapshot from loaded traces"
        />
      </section>

      <ServicesLimitationBanner />

      <section className="services-workspace__body" aria-label="Service groups">
        <ServiceGroupsPanel
          groups={sortedGroups}
          isLoading={isLoading}
          error={error}
          selectedGroupKey={visibleSelectedGroup?.key ?? null}
          onGroupSelect={setSelectedGroup}
          sortField={sortField}
          onSortFieldChange={setSortField}
          totalRequests={serviceGroups.totalRequests}
          groupCount={serviceGroups.groupCount}
        />
        <ServiceGroupDetail group={visibleSelectedGroup} totalRequests={serviceGroups.totalRequests} />
      </section>
    </div>
  )
}

export default Services
