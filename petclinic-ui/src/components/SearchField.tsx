import { Icon } from './Icon'
import styles from './ui.module.css'

interface Props {
  id: string
  label: string
  value: string
  hint?: string
  onChange: (value: string) => void
  onSubmit: () => void
  onClear: () => void
}

export function SearchField({ id, label, value, hint, onChange, onSubmit, onClear }: Props) {
  const hintId = `${id}-hint`
  return (
    <form
      role="search"
      onSubmit={(e) => {
        e.preventDefault()
        onSubmit()
      }}
    >
      <label htmlFor={id} className="visually-hidden">
        {label}
      </label>
      <div className={styles.searchWrap}>
        <span className={styles.searchIcon}>
          <Icon name="search" />
        </span>
        <input
          id={id}
          type="search"
          className={styles.searchInput}
          placeholder={label}
          value={value}
          autoComplete="off"
          aria-describedby={hint ? hintId : undefined}
          onChange={(e) => onChange(e.target.value)}
        />
        {value && (
          <button type="button" className={`${styles.iconButton} ${styles.searchClear}`} aria-label="Clear search" onClick={onClear}>
            <Icon name="close" />
          </button>
        )}
      </div>
      {hint && (
        <p id={hintId} className={styles.hint}>
          {hint}
        </p>
      )}
    </form>
  )
}
