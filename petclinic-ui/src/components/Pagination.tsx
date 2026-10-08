import { Button } from './Button'
import { Icon } from './Icon'
import styles from './ui.module.css'

export const PAGE_SIZES = [10, 20, 50]

interface Props {
  page: number // zero-based
  size: number
  totalElements: number
  totalPages: number
  itemLabel: string // plural noun, e.g. "owners"
  onPageChange: (page: number) => void
  onSizeChange: (size: number) => void
}

export function Pagination({ page, size, totalElements, totalPages, itemLabel, onPageChange, onSizeChange }: Props) {
  const start = totalElements === 0 ? 0 : page * size + 1
  const end = Math.min((page + 1) * size, totalElements)

  return (
    <div className={styles.pagination}>
      <p aria-live="polite">
        Showing {start}–{end} of {totalElements} {itemLabel}
      </p>
      <div className={styles.paginationControls}>
        <label className={styles.rowsSelect}>
          Rows per page
          <select value={size} onChange={(e) => onSizeChange(Number(e.target.value))}>
            {PAGE_SIZES.map((s) => (
              <option key={s} value={s}>
                {s}
              </option>
            ))}
          </select>
        </label>
        <nav aria-label="Pagination" className={styles.paginationNav}>
          <Button onClick={() => onPageChange(page - 1)} disabled={page <= 0}>
            <Icon name="chevronLeft" />
            Previous
          </Button>
          <span>
            Page {page + 1} of {Math.max(totalPages, 1)}
          </span>
          <Button onClick={() => onPageChange(page + 1)} disabled={page + 1 >= totalPages}>
            Next
            <Icon name="chevronRight" />
          </Button>
        </nav>
      </div>
    </div>
  )
}
