import { useEffect, useRef, useState } from 'react'
import { Link, useLocation, useSearchParams } from 'react-router-dom'
import { describeError, isAccessError } from '../../api/client'
import { Button, buttonClassName } from '../../components/Button'
import { EmptyState } from '../../components/EmptyState'
import { ErrorAlert } from '../../components/ErrorAlert'
import { Icon } from '../../components/Icon'
import { PageHeader } from '../../components/PageHeader'
import { PAGE_SIZES, Pagination } from '../../components/Pagination'
import { SearchField } from '../../components/SearchField'
import { useDocumentTitle } from '../../lib/useDocumentTitle'
import { OwnersTable, OwnersTableSkeleton } from './OwnersTable'
import { useOwners } from './useOwners'
import uiStyles from '../../components/ui.module.css'
import styles from './Owners.module.css'

const DEFAULT_SIZE = 20
const SEARCH_DEBOUNCE_MS = 300

function positiveInt(value: string | null, fallback: number): number {
  const parsed = Number.parseInt(value ?? '', 10)
  return Number.isFinite(parsed) && parsed >= 0 ? parsed : fallback
}

export function OwnersPage() {
  useDocumentTitle('Owners')

  // One-time message from another screen, e.g. after deleting an owner. Changing the search clears it.
  const notice = (useLocation().state as { notice?: string } | null)?.notice

  // Search text, page and page size live in the URL so the list survives reloads and back-navigation.
  const [params, setParams] = useSearchParams()
  const lastName = params.get('lastName') ?? ''
  const page = positiveInt(params.get('page'), 0)
  const requestedSize = positiveInt(params.get('size'), DEFAULT_SIZE)
  const size = PAGE_SIZES.includes(requestedSize) ? requestedSize : DEFAULT_SIZE

  // While the user is typing, the field shows `draft`; the URL (and the query) update after a short pause.
  const [draft, setDraft] = useState<string | null>(null)
  const timer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined)
  useEffect(() => () => clearTimeout(timer.current), [])

  function update(changes: Record<string, string | undefined>, replace = false) {
    setParams(
      (prev) => {
        const next = new URLSearchParams(prev)
        for (const [key, value] of Object.entries(changes)) {
          if (value === undefined || value === '') next.delete(key)
          else next.set(key, value)
        }
        return next
      },
      { replace },
    )
  }

  function commitSearch(value: string) {
    clearTimeout(timer.current)
    update({ lastName: value.trim(), page: undefined }, true)
    setDraft(null)
  }

  function onSearchChange(value: string) {
    setDraft(value)
    clearTimeout(timer.current)
    timer.current = setTimeout(() => commitSearch(value), SEARCH_DEBOUNCE_MS)
  }

  const { data, error, isPending, isPlaceholderData, refetch } = useOwners({ lastName, page, size })

  const searching = lastName !== ''
  const subtitle = data
    ? searching
      ? `${data.totalElements} ${data.totalElements === 1 ? 'owner' : 'owners'} matching “${lastName}”`
      : `${data.totalElements} ${data.totalElements === 1 ? 'owner' : 'owners'}`
    : undefined

  let body
  if (isPending) {
    body = <OwnersTableSkeleton />
  } else if (data && data.totalElements === 0) {
    body = searching ? (
      <EmptyState
        title="No owners found"
        action={<Button onClick={() => commitSearch('')}>Show all owners</Button>}
      >
        No owner has a last name starting with “{lastName}”.
      </EmptyState>
    ) : (
      <EmptyState
        title="No owners yet"
        action={
          <Link to="/owners/new" className={buttonClassName('primary')}>
            <Icon name="plus" />
            Add owner
          </Link>
        }
      >
        Add the first owner to get started.
      </EmptyState>
    )
  } else if (data && data.content.length === 0) {
    // The URL asked for a page past the end, e.g. after a search narrowed the results.
    body = (
      <EmptyState
        title="This page is empty"
        action={<Button onClick={() => update({ page: undefined })}>Go to first page</Button>}
      >
        There are no owners on page {page + 1}.
      </EmptyState>
    )
  } else if (data) {
    body = (
      <div className={`${styles.tableWrap} ${isPlaceholderData ? styles.dim : ''}`} aria-busy={isPlaceholderData}>
        <OwnersTable owners={data.content} />
      </div>
    )
  }

  return (
    <>
      <PageHeader
        eyebrow="Clinic administration"
        icon="users"
        title="Owners"
        subtitle={subtitle}
        actions={
          <Link to="/owners/new" className={buttonClassName('primary')}>
            <Icon name="plus" />
            Add owner
          </Link>
        }
      />

      {notice && (
        <p role="status" className={uiStyles.notice}>
          {notice}
        </p>
      )}

      {error && (
        <ErrorAlert
          title={
            isAccessError(error)
              ? "You don't have access to owners"
              : data
                ? 'Could not refresh owners'
                : 'Could not load owners'
          }
          message={
            isAccessError(error)
              ? undefined
              : data
                ? `Showing the last results that loaded. ${describeError(error)}`
                : describeError(error)
          }
          onRetry={isAccessError(error) ? undefined : () => refetch()}
        />
      )}

      <div className={styles.toolbar}>
        <SearchField
          id="owner-search"
          label="Search by last name"
          hint="Matches last names that start with what you type."
          value={draft ?? lastName}
          onChange={onSearchChange}
          onSubmit={() => commitSearch(draft ?? lastName)}
          onClear={() => commitSearch('')}
        />
      </div>

      <div className={styles.tableCard}>
        {body}
        {data && data.totalElements > 0 && (
          <Pagination
            page={page}
            size={size}
            totalElements={data.totalElements}
            totalPages={data.totalPages}
            itemLabel="owners"
            onPageChange={(pageNumber) => update({ page: pageNumber === 0 ? undefined : String(pageNumber) })}
            onSizeChange={(newSize) =>
              update({ size: newSize === DEFAULT_SIZE ? undefined : String(newSize), page: undefined })
            }
          />
        )}
      </div>
    </>
  )
}
