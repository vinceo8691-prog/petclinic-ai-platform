import { useEffect, useRef } from 'react'
import { useLocation } from 'react-router-dom'
import { Button } from './Button'
import { Icon } from './Icon'
import styles from './AppShell.module.css'
import ui from './ui.module.css'

/** Human-readable name of the current screen, shown to the user as the assistant's context. */
export function contextLabel(pathname: string): string {
  if (pathname === '/owners') return 'Owners'
  if (pathname === '/owners/new') return 'Add owner'
  if (/^\/owners\/[^/]+$/.test(pathname)) return 'Owner details'
  return 'PetClinic'
}

interface Props {
  focusOnMount: boolean
  onClose: () => void
}

/**
 * Stub for the AI assistant. It is not connected to petclinic-ai-agent yet; it exists so the
 * shell layout, open/close behavior and keyboard handling are settled before the agent is.
 */
export function AssistantPanel({ focusOnMount, onClose }: Props) {
  const { pathname } = useLocation()
  const headingRef = useRef<HTMLHeadingElement>(null)

  useEffect(() => {
    if (focusOnMount) headingRef.current?.focus()
  }, [focusOnMount])

  return (
    <aside
      id="assistant-panel"
      className={styles.assistant}
      aria-labelledby="assistant-title"
      onKeyDown={(e) => {
        if (e.key === 'Escape') onClose()
      }}
    >
      <div className={styles.assistantHeader}>
        <h2 id="assistant-title" ref={headingRef} tabIndex={-1}>
          Assistant
        </h2>
        <button type="button" className={ui.iconButton} aria-label="Close assistant" onClick={onClose}>
          <Icon name="close" />
        </button>
      </div>
      <p className={styles.assistantContext}>Context: {contextLabel(pathname)}</p>
      <div className={styles.assistantBody}>
        <h3>Not connected yet</h3>
        <p>
          This is a preview of where the assistant will live. Once it is connected you will ask questions here, and any
          change it suggests will appear for you to confirm or reject before anything is saved.
        </p>
      </div>
      <div className={styles.assistantFooter}>
        <input type="text" placeholder="Ask something…" aria-label="Message the assistant" disabled />
        <Button variant="primary" disabled>
          Send
        </Button>
      </div>
    </aside>
  )
}
