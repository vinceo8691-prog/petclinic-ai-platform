import { useEffect, useRef, useState, type SubmitEvent } from 'react'
import { Link } from 'react-router-dom'
import type { OwnerFields } from '../../api/types'
import { Button, buttonClassName } from '../../components/Button'
import { TextField } from '../../components/TextField'
import { FIELD_LABELS, OWNER_FIELD_ORDER, trimOwner, validateOwner, type OwnerField } from './ownerValidation'
import styles from './OwnerForm.module.css'

interface Props {
  initialValues: OwnerFields
  submitLabel: string
  pendingLabel: string
  isPending: boolean
  /** Where Cancel goes. */
  cancelTo: string
  /** Called with trimmed values once they pass validation. */
  onSubmit: (fields: OwnerFields) => void
}

/** The owner form shared by Add owner and Edit owner. The server stays authoritative on validation. */
export function OwnerForm({ initialValues, submitLabel, pendingLabel, isPending, cancelTo, onSubmit }: Props) {
  const [values, setValues] = useState<OwnerFields>(initialValues)
  const [failedSubmits, setFailedSubmits] = useState(0)
  const summary = useRef<HTMLDivElement>(null)

  const allErrors = validateOwner(values)
  // Errors appear after the first submit attempt, then update as the user fixes each field.
  const visible = (field: OwnerField) => (failedSubmits > 0 ? allErrors[field] : undefined)
  const summaryFields = OWNER_FIELD_ORDER.filter((field) => failedSubmits > 0 && allErrors[field])

  useEffect(() => {
    if (failedSubmits > 0) summary.current?.focus()
  }, [failedSubmits])

  function bind(field: OwnerField) {
    return {
      value: values[field],
      onChange: (event: { target: { value: string } }) =>
        setValues((current) => ({ ...current, [field]: event.target.value })),
    }
  }

  function handleSubmit(event: SubmitEvent) {
    event.preventDefault()
    if (Object.keys(allErrors).length > 0) {
      setFailedSubmits((count) => count + 1)
      return
    }
    onSubmit(trimOwner(values))
  }

  function focusField(field: OwnerField) {
    document.getElementById(`owner-${field}`)?.focus()
  }

  return (
    <div className={styles.formPanel}>
      <form className={styles.form} onSubmit={handleSubmit} noValidate>
        <p className={styles.note}>All fields are required.</p>

        {summaryFields.length > 0 && (
          <div ref={summary} tabIndex={-1} role="alert" className={styles.summary}>
            <strong>Please fix the following:</strong>
            <ul>
              {summaryFields.map((summaryField) => (
                <li key={summaryField}>
                  <a
                    href={`#owner-${summaryField}`}
                    onClick={(event) => {
                      event.preventDefault()
                      focusField(summaryField)
                    }}
                  >
                    {FIELD_LABELS[summaryField]}
                  </a>
                  : {allErrors[summaryField]}
                </li>
              ))}
            </ul>
          </div>
        )}

        <div className={styles.row}>
          <TextField id="owner-firstName" label="First name" error={visible('firstName')} autoComplete="given-name" {...bind('firstName')} />
          <TextField id="owner-lastName" label="Last name" error={visible('lastName')} autoComplete="family-name" {...bind('lastName')} />
        </div>
        <TextField id="owner-address" label="Address" error={visible('address')} autoComplete="street-address" {...bind('address')} />
        <TextField id="owner-city" label="City" error={visible('city')} autoComplete="address-level2" {...bind('city')} />
        <TextField
          id="owner-telephone"
          label="Telephone"
          hint="10 digits, no spaces or dashes."
          error={visible('telephone')}
          type="tel"
          inputMode="numeric"
          autoComplete="tel"
          {...bind('telephone')}
        />

        <div className={styles.buttons}>
          <Button type="submit" variant="primary" disabled={isPending}>
            {isPending ? pendingLabel : submitLabel}
          </Button>
          <Link to={cancelTo} className={buttonClassName('secondary')}>
            Cancel
          </Link>
        </div>
      </form>
    </div>
  )
}
