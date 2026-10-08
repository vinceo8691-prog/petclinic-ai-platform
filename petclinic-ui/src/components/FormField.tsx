import type { ReactNode } from 'react'
import { Icon } from './Icon'
import styles from './ui.module.css'

interface InputProps {
  id: string
  className: string
  'aria-invalid': boolean
  'aria-describedby': string | undefined
}

interface Props {
  id: string
  label: string
  hint?: string
  error?: string
  children: (inputProps: InputProps) => ReactNode
}

/** Label above the control, optional hint, inline error. The control comes from a render prop so it can be any input type. */
export function FormField({ id, label, hint, error, children }: Props) {
  const hintId = `${id}-hint`
  const errorId = `${id}-error`
  const describedBy = [error ? errorId : undefined, hint ? hintId : undefined].filter(Boolean).join(' ') || undefined

  return (
    <div className={styles.field}>
      <label htmlFor={id} className={styles.label}>
        {label}
      </label>
      {children({ id, className: styles.input, 'aria-invalid': Boolean(error), 'aria-describedby': describedBy })}
      {hint && (
        <p id={hintId} className={styles.hint}>
          {hint}
        </p>
      )}
      {error && (
        <p id={errorId} className={styles.error}>
          <Icon name="alert" />
          {error}
        </p>
      )}
    </div>
  )
}
