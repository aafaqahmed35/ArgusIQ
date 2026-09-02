import { useState } from 'react'

const SESSION_STORAGE_KEY = 'argusiq-alerts-limitation-dismissed'

function DerivedAlertsBanner({ recentTraceLimit }) {
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
    <section className="services-limitation-banner derived-alerts-banner" aria-label="Derived alerts notice" role="note">
      <p className="services-limitation-banner__message">
        Alerts are derived from at most {recentTraceLimit} recent traces and connection state. They are not
        historical evaluations; ArgusIQ does not persist alert history or receive notifications from a backend alerts API.
      </p>
      <button className="services-limitation-banner__dismiss" type="button" onClick={handleDismiss}>
        Dismiss
      </button>
    </section>
  )
}

export default DerivedAlertsBanner
