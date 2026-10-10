import type { ComponentProps } from 'react'
import { Icon } from './Icon'
import styles from './ui.module.css'

interface Props extends Omit<ComponentProps<'input'>, 'className'> {
  /** Required: it links the label to the input. */
  id: string
  label: string
  hint?: string
  error?: string
}

/** Label above the input, optional hint, inline error. Any other prop goes to the <input>. */
export function TextField({ id, label, hint, error, ...inputProps }: Props) {
  const hintId = `${id}-hint`
  const errorId = `${id}-error`
  const describedBy = [error ? errorId : undefined, hint ? hintId : undefined].filter(Boolean).join(' ') || undefined

  return (
    <div className={styles.field}>
      <label htmlFor={id} className={styles.label}>
        {label}
      </label>
      <input
        {...inputProps}
        id={id}
        className={styles.input}
        aria-invalid={Boolean(error)}
        aria-describedby={describedBy}
      />
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
