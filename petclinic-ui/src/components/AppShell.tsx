import { useEffect, useRef, useState } from 'react'
import { Link, matchPath, NavLink, Outlet, useLocation } from 'react-router-dom'
import { AssistantPanel } from './AssistantPanel'
import { Backdrop, PageScene } from './Backdrop'
import { DogMark } from './DogMark'
import { Icon } from './Icon'
import styles from './AppShell.module.css'

const ASSISTANT_KEY = 'petclinic.assistantOpen'

// Only the open/closed preference is stored. Conversation content may contain personal data and is never persisted.
function readAssistantOpen(): boolean {
  try {
    return localStorage.getItem(ASSISTANT_KEY) === 'true'
  } catch {
    return false
  }
}

function writeAssistantOpen(open: boolean) {
  try {
    localStorage.setItem(ASSISTANT_KEY, String(open))
  } catch {
    // storage unavailable: the preference just won't persist
  }
}

export function AppShell() {
  const { pathname } = useLocation()
  const [assistantOpen, setAssistantOpen] = useState(readAssistantOpen)
  const [focusAssistant, setFocusAssistant] = useState(false)
  const launcher = useRef<HTMLButtonElement>(null)
  const restoreLauncherFocus = useRef(false)
  const main = useRef<HTMLElement>(null)
  const previousPathname = useRef(pathname)

  // After navigating to a new screen, move focus to the content so keyboard and screen reader users start there.
  // Comparing paths (not "is this the first run") keeps this correct when StrictMode runs effects twice in dev.
  useEffect(() => {
    if (previousPathname.current === pathname) return
    previousPathname.current = pathname
    main.current?.focus()
  }, [pathname])

  // The launcher only exists while the panel is closed, so focus returns to it after it re-renders.
  useEffect(() => {
    if (!assistantOpen && restoreLauncherFocus.current) {
      restoreLauncherFocus.current = false
      launcher.current?.focus()
    }
  }, [assistantOpen])

  function openAssistant() {
    setAssistantOpen(true)
    setFocusAssistant(true)
    writeAssistantOpen(true)
  }

  function closeAssistant() {
    restoreLauncherFocus.current = true
    setAssistantOpen(false)
    setFocusAssistant(false)
    writeAssistantOpen(false)
  }

  return (
    <div className={styles.shell}>
      <a href="#main" className="skip-link">
        Skip to content
      </a>
      <header className={styles.header}>
        <Link to="/owners" className={styles.brand}>
          <DogMark />
          PetClinic
        </Link>
        <nav aria-label="Main" className={styles.nav}>
          <NavLink to="/owners" className={({ isActive }) => `${styles.navLink} ${isActive ? styles.navLinkActive : ''}`}>
            Owners
          </NavLink>
        </nav>
      </header>
      <Backdrop />
      <div className={styles.body}>
        <main id="main" ref={main} tabIndex={-1} className={styles.main}>
          <div className={`${styles.card} ${matchPath('/owners', pathname) ? '' : styles.cardNarrow}`}>
            <Outlet />
          </div>
          <PageScene />
        </main>
        {assistantOpen && <AssistantPanel focusOnMount={focusAssistant} onClose={closeAssistant} />}
      </div>
      {!assistantOpen && (
        <button ref={launcher} type="button" className={styles.launcher} onClick={openAssistant}>
          <Icon name="chat" />
          Assistant
        </button>
      )}
    </div>
  )
}
