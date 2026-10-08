import { useEffect, useRef, useState, type SubmitEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { describeError } from '../../api/client'
import type { OwnerFields } from '../../api/types'
import { Button, buttonClassName } from '../../components/Button'
import { ErrorAlert } from '../../components/ErrorAlert'
import { FormField } from '../../components/FormField'
import { Icon } from '../../components/Icon'
import { PageHeader } from '../../components/PageHeader'
import { useDocumentTitle } from '../../lib/useDocumentTitle'
import { FIELD_LABELS, OWNER_FIELD_ORDER, trimOwner, validateOwner, type OwnerField } from './ownerValidation'
import { useAddOwner } from './useOwners'
import ui from '../../components/ui.module.css'
import styles from './AddOwnerPage.module.css'

const EMPTY: OwnerFields = { firstName: '', lastName: '', address: '', city: '', telephone: '' }

export function AddOwnerPage() {
  useDocumentTitle('Add owner')
  const navigate = useNavigate()
  const addOwner = useAddOwner()

  const [values, setValues] = useState<OwnerFields>(EMPTY)
  const [touched, setTouched] = useState<Partial<Record<OwnerField, boolean>>>({})
  const [failedSubmits, setFailedSubmits] = useState(0)
  const summary = useRef<HTMLDivElement>(null)

  const allErrors = validateOwner(values)
  // An error shows once the field has been left, or after a submit attempt.
  const visible = (field: OwnerField) => (touched[field] || failedSubmits > 0 ? allErrors[field] : undefined)
  const summaryFields = OWNER_FIELD_ORDER.filter((f) => failedSubmits > 0 && allErrors[f])

  useEffect(() => {
    if (failedSubmits > 0) summary.current?.focus()
  }, [failedSubmits])

  function bind(field: OwnerField) {
    return {
      value: values[field],
      onChange: (e: { target: { value: string } }) => setValues((v) => ({ ...v, [field]: e.target.value })),
      onBlur: () => setTouched((t) => ({ ...t, [field]: true })),
    }
  }

  function onSubmit(event: SubmitEvent) {
    event.preventDefault()
    if (Object.keys(allErrors).length > 0) {
      setFailedSubmits((n) => n + 1)
      return
    }
    addOwner.mutate(trimOwner(values), { onSuccess: (owner) => navigate(`/owners/${owner.id}`) })
  }

  function focusField(field: OwnerField) {
    document.getElementById(`owner-${field}`)?.focus()
  }

  return (
    <>
      <Link to="/owners" className={styles.back}>
        <Icon name="chevronLeft" />
        Owners
      </Link>
      <PageHeader title="Add owner" />

      {addOwner.error && <ErrorAlert title="Could not save owner" message={describeError(addOwner.error)} />}

      <div className={`${ui.panel} ${styles.formPanel}`}>
        <form className={styles.form} onSubmit={onSubmit} noValidate>
          <p className={styles.note}>All fields are required.</p>

          {summaryFields.length > 0 && (
            <div ref={summary} tabIndex={-1} role="alert" className={styles.summary}>
              <strong>Please fix the following:</strong>
              <ul>
                {summaryFields.map((summaryField) => (
                  <li key={summaryField}>
                    <a
                      href={`#owner-${summaryField}`}
                      onClick={(e) => {
                        e.preventDefault()
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
            <FormField id="owner-firstName" label="First name" error={visible('firstName')}>
              {(p) => <input {...p} type="text" autoComplete="given-name" {...bind('firstName')} />}
            </FormField>
            <FormField id="owner-lastName" label="Last name" error={visible('lastName')}>
              {(p) => <input {...p} type="text" autoComplete="family-name" {...bind('lastName')} />}
            </FormField>
          </div>
          <FormField id="owner-address" label="Address" error={visible('address')}>
            {(p) => <input {...p} type="text" autoComplete="street-address" {...bind('address')} />}
          </FormField>
          <FormField id="owner-city" label="City" error={visible('city')}>
            {(p) => <input {...p} type="text" autoComplete="address-level2" {...bind('city')} />}
          </FormField>
          <FormField id="owner-telephone" label="Telephone" hint="Digits only, no spaces or dashes." error={visible('telephone')}>
            {(p) => <input {...p} type="tel" inputMode="numeric" autoComplete="tel" {...bind('telephone')} />}
          </FormField>

          <div className={styles.buttons}>
            <Button type="submit" variant="primary" disabled={addOwner.isPending}>
              {addOwner.isPending ? 'Saving…' : 'Save owner'}
            </Button>
            <Link to="/owners" className={buttonClassName('secondary')}>
              Cancel
            </Link>
          </div>
        </form>
      </div>
    </>
  )
}
