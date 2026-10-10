import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { describeError } from '../../api/client'
import type { Owner, Pet } from '../../api/types'
import { BackLink } from '../../components/BackLink'
import { ConfirmDialog } from '../../components/ConfirmDialog'
import { Icon } from '../../components/Icon'
import { PageHeader } from '../../components/PageHeader'
import { formatDate, formatPhone } from '../../lib/format'
import { useDocumentTitle } from '../../lib/useDocumentTitle'
import { OwnerLoadStatus, OwnerNotFound, useOwnerFromRoute } from './OwnerRoute'
import { useDeleteOwner } from './useOwners'
import ui from '../../components/ui.module.css'
import styles from './OwnerDetails.module.css'

export function OwnerDetailsPage() {
  const { owner, notFound, loadState } = useOwnerFromRoute()
  useDocumentTitle(owner ? `${owner.firstName} ${owner.lastName}` : 'Owner details')

  if (notFound) return <OwnerNotFound />

  return (
    <>
      <BackLink to="/owners">Owners</BackLink>
      <OwnerLoadStatus {...loadState} />
      {owner && <OwnerDetails owner={owner} />}
    </>
  )
}

function OwnerDetails({ owner }: { owner: Owner }) {
  const navigate = useNavigate()
  const deleteOwner = useDeleteOwner(owner.id)
  const [confirmingDelete, setConfirmingDelete] = useState(false)
  const fullName = `${owner.firstName} ${owner.lastName}`

  function confirmDelete() {
    deleteOwner.mutate(undefined, {
      // The owners list shows this message once, taken from the navigation state.
      onSuccess: () => navigate('/owners', { replace: true, state: { notice: `Deleted owner ${fullName}.` } }),
    })
  }

  return (
    <>
      <PageHeader
        icon="users"
        title={fullName}
        actions={
          <>
            <Link
              to={`/owners/${owner.id}/edit`}
              className={ui.iconAction}
              aria-label="Edit owner"
              title="Edit owner"
            >
              <Icon name="edit" size={18} />
            </Link>
            <button
              type="button"
              className={`${ui.iconAction} ${styles.deleteButton}`}
              aria-label="Delete owner"
              title="Delete owner"
              onClick={() => {
                deleteOwner.reset()
                setConfirmingDelete(true)
              }}
            >
              <Icon name="trash" size={18} />
            </button>
          </>
        }
      />

      <section aria-labelledby="contact-heading" className={styles.section}>
        <h2 id="contact-heading">Contact</h2>
        <dl className={styles.details}>
          <div>
            <dt>Address</dt>
            <dd>{owner.address}</dd>
          </div>
          <div>
            <dt>City</dt>
            <dd>{owner.city}</dd>
          </div>
          <div>
            <dt>Telephone</dt>
            <dd>{formatPhone(owner.telephone)}</dd>
          </div>
        </dl>
      </section>

      <section aria-labelledby="pets-heading" className={styles.section}>
        <h2 id="pets-heading">Pets ({owner.pets.length})</h2>
        {owner.pets.length === 0 ? (
          <p className={styles.none}>This owner has no pets yet.</p>
        ) : (
          <ul className={styles.pets}>
            {owner.pets.map((pet) => (
              <PetCard key={pet.id} pet={pet} />
            ))}
          </ul>
        )}
      </section>

      {confirmingDelete && (
        <ConfirmDialog
          title={`Delete ${fullName}?`}
          confirmLabel="Delete"
          pendingLabel="Deleting…"
          isPending={deleteOwner.isPending}
          errorMessage={deleteOwner.error ? describeError(deleteOwner.error) : undefined}
          onConfirm={confirmDelete}
          onCancel={() => setConfirmingDelete(false)}
        >
          <p>
            This permanently deletes the owner
            {owner.pets.length > 0 && `, ${petCount(owner.pets.length)} and all of their visits`}. It can't be
            undone.
          </p>
        </ConfirmDialog>
      )}
    </>
  )
}

function petCount(count: number): string {
  return count === 1 ? '1 pet' : `${count} pets`
}

function PetCard({ pet }: { pet: Pet }) {
  return (
    <li className={styles.pet}>
      <div className={styles.petHeader}>
        <h3>{pet.name}</h3>
        <span className={styles.petType}>{pet.type.name}</span>
      </div>
      <p className={styles.petMeta}>Born {formatDate(pet.birthDate)}</p>
      {pet.visits.length === 0 ? (
        <p className={styles.none}>No visits recorded.</p>
      ) : (
        <ul className={styles.visits} aria-label={`Visits for ${pet.name}`}>
          {pet.visits.map((visit) => (
            <li key={visit.id}>
              <span className={styles.visitDate}>{formatDate(visit.date)}</span> {visit.description}
            </li>
          ))}
        </ul>
      )}
    </li>
  )
}
