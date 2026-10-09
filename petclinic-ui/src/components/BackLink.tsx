import { Link } from 'react-router-dom'
import { Icon } from './Icon'
import styles from './ui.module.css'

export function BackLink({ to, children }: { to: string; children: string }) {
  return (
    <Link to={to} className={styles.back}>
      <Icon name="chevronLeft" />
      {children}
    </Link>
  )
}
