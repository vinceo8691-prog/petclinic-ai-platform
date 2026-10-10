import { useEffect, useRef, type ReactNode } from 'react'
import { Button } from './Button'
import { ErrorAlert } from './ErrorAlert'
import styles from './ConfirmDialog.module.css'

interface Props {
  title: string
  children: ReactNode
  confirmLabel: string
  pendingLabel: string
  isPending: boolean
  /** Shown inside the dialog when the confirmed action failed, so the user can retry or cancel. */
  errorMessage?: string
  onConfirm: () => void
  onCancel: () => void
}

/**
 * A modal confirmation built on the native <dialog>: it traps focus and closes on Esc, and
 * focus returns to the control that opened it. Render it only while it should be open.
 * Cancel has initial focus, so Enter never confirms by accident.
 */
export function ConfirmDialog({
  title,
  children,
  confirmLabel,
  pendingLabel,
  isPending,
  errorMessage,
  onConfirm,
  onCancel,
}: Props) {
  const dialog = useRef<HTMLDialogElement>(null)
  const cancelButton = useRef<HTMLButtonElement>(null)

  useEffect(() => {
    const element = dialog.current
    const opener = document.activeElement
    element?.showModal()
    cancelButton.current?.focus()
    return () => {
      if (element?.open) element.close()
      // React has already removed the dialog by now, so the browser cannot restore focus itself.
      if (opener instanceof HTMLElement && opener.isConnected) opener.focus()
    }
  }, [])

  return (
    <dialog
      ref={dialog}
      className={styles.dialog}
      aria-labelledby="confirm-dialog-title"
      onCancel={(event) => {
        // Esc: let the parent unmount us, but not while the request is in flight.
        event.preventDefault()
        if (!isPending) onCancel()
      }}
    >
      <h2 id="confirm-dialog-title">{title}</h2>
      <div className={styles.body}>{children}</div>
      {errorMessage && <ErrorAlert title="Could not complete the action" message={errorMessage} />}
      <div className={styles.buttons}>
        <Button ref={cancelButton} onClick={onCancel} disabled={isPending}>
          Cancel
        </Button>
        <Button variant="danger" onClick={onConfirm} disabled={isPending}>
          {isPending ? pendingLabel : confirmLabel}
        </Button>
      </div>
    </dialog>
  )
}
