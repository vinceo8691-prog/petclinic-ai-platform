import { Link, useParams } from 'react-router-dom'
import { EmptyState } from '../../components/EmptyState'
import { PageHeader } from '../../components/PageHeader'
import { buttonClassName } from '../../components/Button'
import { useDocumentTitle } from '../../lib/useDocumentTitle'
import ui from '../../components/ui.module.css'

/** Placeholder so owner links and "Add owner" have somewhere to land. The real screen is a later slice. */
export function OwnerDetailsPage() {
  useDocumentTitle('Owner details')
  const { ownerId } = useParams()

  return (
    <>
      <PageHeader title="Owner details" subtitle={`Owner #${ownerId}`} />
      <div className={ui.panel}>
        <EmptyState
          title="Not built yet"
          action={
            <Link to="/owners" className={buttonClassName('secondary')}>
              Back to owners
            </Link>
          }
        >
          This screen is planned but hasn't been built yet.
        </EmptyState>
      </div>
    </>
  )
}
