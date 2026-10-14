import { useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import { describeWriteError } from '../../api/client'
import type { Owner } from '../../api/types'
import { BackLink } from '../../components/BackLink'
import { ErrorAlert } from '../../components/ErrorAlert'
import { PageHeader } from '../../components/PageHeader'
import { useDocumentTitle } from '../../lib/useDocumentTitle'
import { OwnerForm } from './OwnerForm'
import { OwnerLoadStatus, OwnerNotFound, useOwnerFromRoute } from './OwnerRoute'
import { ownerErrorsFromServer } from './ownerValidation'
import { useUpdateOwner } from './useOwners'

export function EditOwnerPage() {
  const { owner, ownerId, notFound, loadState } = useOwnerFromRoute()
  useDocumentTitle('Edit owner')

  if (notFound) return <OwnerNotFound />

  return (
    <>
      <BackLink to={`/owners/${ownerId}`}>Owner details</BackLink>
      <PageHeader icon="users" title="Edit owner" />
      <OwnerLoadStatus {...loadState} />
      {owner && <EditOwnerForm key={owner.id} owner={owner} />}
    </>
  )
}

function EditOwnerForm({ owner }: { owner: Owner }) {
  const navigate = useNavigate()
  const updateOwner = useUpdateOwner(owner.id)
  const serverErrors = useMemo(() => ownerErrorsFromServer(updateOwner.error), [updateOwner.error])
  const hasFieldErrors = Object.keys(serverErrors).length > 0
  const detailsPath = `/owners/${owner.id}`

  return (
    <>
      {updateOwner.error && !hasFieldErrors && (
        <ErrorAlert title="Could not save changes" message={describeWriteError(updateOwner.error)} />
      )}
      <OwnerForm
        initialValues={{
          firstName: owner.firstName,
          lastName: owner.lastName,
          address: owner.address,
          city: owner.city,
          telephone: owner.telephone,
        }}
        submitLabel="Save changes"
        pendingLabel="Saving…"
        isPending={updateOwner.isPending}
        cancelTo={detailsPath}
        serverErrors={serverErrors}
        onSubmit={(fields) => updateOwner.mutate(fields, { onSuccess: () => navigate(detailsPath) })}
      />
    </>
  )
}
