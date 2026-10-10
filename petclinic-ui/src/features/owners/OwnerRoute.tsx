import { Link, useParams } from 'react-router-dom'
import { ApiError, describeError, isAccessError } from '../../api/client'
import { buttonClassName } from '../../components/Button'
import { EmptyState } from '../../components/EmptyState'
import { ErrorAlert } from '../../components/ErrorAlert'
import { useDocumentTitle } from '../../lib/useDocumentTitle'
import { useOwner } from './useOwners'
import styles from './OwnerDetails.module.css'

/** The route param as an owner id, or undefined when it is not a whole number (so no request is made). */
function parseOwnerId(value: string | undefined): number | undefined {
  return value !== undefined && /^\d+$/.test(value) ? Number(value) : undefined
}

/**
 * Loads the owner named in the URL (/owners/:ownerId/...). Shared by the details and edit pages so
 * both handle "not a number", 404, loading and errors the same way. Pages render <OwnerNotFound />
 * when `notFound` is set and <OwnerLoadStatus {...loadState} /> while there is no owner to show.
 */
export function useOwnerFromRoute() {
  const ownerId = parseOwnerId(useParams().ownerId)
  const { data: owner, error, isPending, refetch } = useOwner(ownerId)
  const notFound = ownerId === undefined || (error instanceof ApiError && error.status === 404)

  return {
    owner,
    ownerId,
    notFound,
    loadState: { isPending, error, onRetry: () => void refetch() },
  }
}

interface LoadStatusProps {
  isPending: boolean
  error: Error | null
  onRetry: () => void
}

/** "Loading owner…" or the load error. Renders nothing once the owner is there. */
export function OwnerLoadStatus({ isPending, error, onRetry }: LoadStatusProps) {
  if (error) {
    const noAccess = isAccessError(error)
    return (
      <ErrorAlert
        title={noAccess ? "You don't have access to this owner" : 'Could not load owner'}
        message={describeError(error)}
        onRetry={noAccess ? undefined : onRetry}
      />
    )
  }
  if (isPending) {
    return (
      <p role="status" className={styles.loading}>
        Loading owner…
      </p>
    )
  }
  return null
}

export function OwnerNotFound() {
  useDocumentTitle('Owner not found')
  return (
    <EmptyState
      title="Owner not found"
      action={
        <Link to="/owners" className={buttonClassName('secondary')}>
          Back to owners
        </Link>
      }
    >
      This owner doesn't exist. It may have been deleted.
    </EmptyState>
  )
}
