import { useNavigate } from 'react-router-dom'
import { describeError } from '../../api/client'
import type { OwnerFields } from '../../api/types'
import { BackLink } from '../../components/BackLink'
import { ErrorAlert } from '../../components/ErrorAlert'
import { PageHeader } from '../../components/PageHeader'
import { useDocumentTitle } from '../../lib/useDocumentTitle'
import { OwnerForm } from './OwnerForm'
import { useAddOwner } from './useOwners'

const EMPTY: OwnerFields = { firstName: '', lastName: '', address: '', city: '', telephone: '' }

export function AddOwnerPage() {
  useDocumentTitle('Add owner')
  const navigate = useNavigate()
  const addOwner = useAddOwner()

  return (
    <>
      <BackLink to="/owners">Owners</BackLink>
      <PageHeader icon="users" title="Add owner" />

      {addOwner.error && <ErrorAlert title="Could not save owner" message={describeError(addOwner.error)} />}

      <OwnerForm
        initialValues={EMPTY}
        submitLabel="Save owner"
        pendingLabel="Saving…"
        isPending={addOwner.isPending}
        cancelTo="/owners"
        onSubmit={(fields) => addOwner.mutate(fields, { onSuccess: (owner) => navigate(`/owners/${owner.id}`) })}
      />
    </>
  )
}
