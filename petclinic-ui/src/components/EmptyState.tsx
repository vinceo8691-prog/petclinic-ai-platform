import type { ReactNode } from 'react'
import styles from './ui.module.css'

interface Props {
  title: string
  children?: ReactNode
  action?: ReactNode
}

export function EmptyState({ title, children, action }: Props) {
  return (
    <div className={styles.emptyState}>
      <h2>{title}</h2>
      {children && <p>{children}</p>}
      {action}
    </div>
  )
}
