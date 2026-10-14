import { useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import { describeWriteError } from '../../api/client'
import type { OwnerFields } from '../../api/types'
import { BackLink } from '../../components/BackLink'
import { ErrorAlert } from '../../components/ErrorAlert'
import { PageHeader } from '../../components/PageHeader'
import { useDocumentTitle } from '../../lib/useDocumentTitle'
import { OwnerForm } from './OwnerForm'
import { ownerErrorsFromServer } from './ownerValidation'
import { useAddOwner } from './useOwners'

const EMPTY: OwnerFields = { firstName: '', lastName: '', address: '', city: '', telephone: '' }

export function AddOwnerPage() {
  useDocumentTitle('Add owner')
  const navigate = useNavigate()
  const addOwner = useAddOwner()
  const serverErrors = useMemo(() => ownerErrorsFromServer(addOwner.error), [addOwner.error])
  const hasFieldErrors = Object.keys(serverErrors).length > 0

  return (
    <>
      <BackLink to="/owners">Owners</BackLink>
      <PageHeader icon="users" title="Add owner" />

      {addOwner.error && !hasFieldErrors && (
        <ErrorAlert title="Could not save owner" message={describeWriteError(addOwner.error)} />
      )}

      <OwnerForm
        initialValues={EMPTY}
        submitLabel="Save owner"
        pendingLabel="Saving…"
        isPending={addOwner.isPending}
        cancelTo="/owners"
        serverErrors={serverErrors}
        onSubmit={(fields) => addOwner.mutate(fields, { onSuccess: (owner) => navigate(`/owners/${owner.id}`) })}
      />
    </>
  )
}
