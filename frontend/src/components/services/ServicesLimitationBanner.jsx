import { useState } from 'react'

const SESSION_STORAGE_KEY = 'argusiq-services-limitation-dismissed'

function ServicesLimitationBanner({ recentTraceLimit }) {
  const [isVisible, setIsVisible] = useState(
    () => sessionStorage.getItem(SESSION_STORAGE_KEY) !== 'true',
  )

  const handleDismiss = () => {
    sessionStorage.setItem(SESSION_STORAGE_KEY, 'true')
    setIsVisible(false)
  }

  if (!isVisible) {
    return null
  }

  return (
    <section className="services-limitation-banner" aria-label="Service grouping notice" role="note">
      <p className="services-limitation-banner__message">
        This recent-activity view groups URI prefixes across at most {recentTraceLimit} traces. It is not a complete
        service inventory or an authoritative service aggregate.
      </p>
      <button className="services-limitation-banner__dismiss" type="button" onClick={handleDismiss}>
        Dismiss
      </button>
    </section>
  )
}

export default ServicesLimitationBanner
