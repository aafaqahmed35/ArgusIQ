import { useEffect, useReducer, useRef } from 'react'
import { NavLink, Outlet } from 'react-router-dom'
import { WORKSPACES } from '../../config/workspaceConfig'
import { getPrimaryWorkspaces, mobileNavigationReducer } from './appShellNavigation'

const FOCUSABLE_SELECTOR = 'a[href], button:not([disabled]), [tabindex]:not([tabindex="-1"])'
const PRIMARY_WORKSPACES = getPrimaryWorkspaces(WORKSPACES)

function Brand({ compact = false }) {
  return (
    <div className={`app-sidebar__brand ${compact ? 'app-sidebar__brand--compact' : ''}`}>
      <span className="app-sidebar__mark" aria-hidden="true" />
      <div>
        <strong>ArgusIQ</strong>
        <span>Observability</span>
      </div>
    </div>
  )
}

function NavigationLinks({ onNavigate }) {
  return PRIMARY_WORKSPACES.map((workspace) => (
    <NavLink
      className={({ isActive }) => `app-sidebar__link ${isActive ? 'is-active' : ''}`}
      end={workspace.path === '/'}
      key={workspace.id}
      onClick={onNavigate}
      to={workspace.path}
    >
      <span className="app-sidebar__icon" aria-hidden="true" />
      <span>{workspace.label}</span>
    </NavLink>
  ))
}

function AppShell() {
  const [isMobileNavigationOpen, dispatchNavigation] = useReducer(mobileNavigationReducer, false)
  const mobileNavigationRef = useRef(null)
  const mobileNavigationCloseRef = useRef(null)
  const mobileNavigationTriggerRef = useRef(null)
  const mainContentRef = useRef(null)

  const closeMobileNavigation = ({ reason = 'close', restoreTrigger = true } = {}) => {
    dispatchNavigation({ type: reason })

    if (restoreTrigger) {
      window.requestAnimationFrame(() => mobileNavigationTriggerRef.current?.focus())
    }
  }

  const handleMobileNavigate = () => {
    dispatchNavigation({ type: 'route-selected' })
    window.requestAnimationFrame(() => mainContentRef.current?.focus())
  }

  const handleMobileNavigationKeyDown = (event) => {
    if (event.key === 'Escape') {
      event.preventDefault()
      closeMobileNavigation({ reason: 'escape' })
      return
    }

    if (event.key !== 'Tab') return

    const focusableElements = [...mobileNavigationRef.current.querySelectorAll(FOCUSABLE_SELECTOR)]
    if (focusableElements.length === 0) return

    const firstElement = focusableElements[0]
    const lastElement = focusableElements.at(-1)

    if (event.shiftKey && document.activeElement === firstElement) {
      event.preventDefault()
      lastElement.focus()
    } else if (!event.shiftKey && document.activeElement === lastElement) {
      event.preventDefault()
      firstElement.focus()
    }
  }

  useEffect(() => {
    if (!isMobileNavigationOpen) return undefined

    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    mobileNavigationCloseRef.current?.focus()

    return () => {
      document.body.style.overflow = previousOverflow
    }
  }, [isMobileNavigationOpen])

  useEffect(() => {
    const desktopQuery = window.matchMedia('(min-width: 981px)')
    const closeAtDesktopWidth = (event) => {
      if (event.matches) dispatchNavigation({ type: 'desktop-resize' })
    }

    desktopQuery.addEventListener('change', closeAtDesktopWidth)
    return () => desktopQuery.removeEventListener('change', closeAtDesktopWidth)
  }, [])

  return (
    <div className="app-shell">
      <a className="app-shell__skip-link" href="#main-content">Skip to content</a>

      <aside className="app-sidebar" aria-label="Primary navigation">
        <Brand />

        <nav className="app-sidebar__nav">
          <NavigationLinks />
        </nav>

        <div className="app-sidebar__workspace">
          <span className="app-sidebar__workspace-mark" aria-hidden="true">AI</span>
          <div>
            <strong>ArgusIQ workspace</strong>
            <span>Observability console</span>
          </div>
        </div>
      </aside>

      <header className="app-mobile-bar">
        <Brand compact />
        <button
          aria-controls="mobile-primary-navigation"
          aria-expanded={isMobileNavigationOpen}
          aria-label="Open primary navigation"
          className="app-mobile-bar__menu"
          onClick={() => dispatchNavigation({ type: 'open' })}
          ref={mobileNavigationTriggerRef}
          type="button"
        >
          <span aria-hidden="true" />
          <span aria-hidden="true" />
          <span aria-hidden="true" />
        </button>
      </header>

      {isMobileNavigationOpen ? (
        <div className="app-mobile-navigation-layer">
          <button
            aria-label="Close primary navigation"
            className="app-mobile-navigation__backdrop"
            onClick={() => closeMobileNavigation({ reason: 'backdrop' })}
            tabIndex={-1}
            type="button"
          />
          <aside
            aria-labelledby="mobile-navigation-title"
            aria-modal="true"
            className="app-mobile-navigation"
            id="mobile-primary-navigation"
            onKeyDown={handleMobileNavigationKeyDown}
            ref={mobileNavigationRef}
            role="dialog"
          >
            <div className="app-mobile-navigation__header">
              <div>
                <span className="app-mobile-navigation__eyebrow">Workspace</span>
                <strong id="mobile-navigation-title">Primary navigation</strong>
              </div>
              <button
                aria-label="Close primary navigation"
                className="app-mobile-navigation__close"
                onClick={() => closeMobileNavigation()}
                ref={mobileNavigationCloseRef}
                type="button"
              >
                <span aria-hidden="true">×</span>
              </button>
            </div>
            <nav aria-label="Primary navigation" className="app-sidebar__nav app-mobile-navigation__nav">
              <NavigationLinks onNavigate={handleMobileNavigate} />
            </nav>
            <p className="app-mobile-navigation__note">Infrastructure and Settings are not yet available in this production workspace.</p>
          </aside>
        </div>
      ) : null}

      <main className="dashboard-page" id="main-content" ref={mainContentRef} tabIndex={-1}>
        <Outlet />
      </main>
    </div>
  )
}

export default AppShell
