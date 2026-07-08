import { useState } from 'react'

const SESSION_STORAGE_KEY = 'argusiq-services-limitation-dismissed'

function ServicesLimitationBanner() {
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
        Services are derived from URI prefixes because the backend currently does not expose service metadata.
      </p>
      <button className="services-limitation-banner__dismiss" type="button" onClick={handleDismiss}>
        Dismiss
      </button>
    </section>
  )
}

export default ServicesLimitationBanner
