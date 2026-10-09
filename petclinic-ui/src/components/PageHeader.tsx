import type { ReactNode } from 'react'
import { Icon, type IconName } from './Icon'
import styles from './ui.module.css'

interface Props {
  title: string
  subtitle?: ReactNode
  actions?: ReactNode
  /** Small uppercase label above the title, e.g. the area of the app. */
  eyebrow?: string
  /** Icon shown in a tile beside the title. */
  icon?: IconName
}

export function PageHeader({ title, subtitle, actions, eyebrow, icon }: Props) {
  return (
    <div className={styles.pageHeader}>
      <div>
        {eyebrow && (
          <p className={styles.eyebrow}>
            <span className={styles.eyebrowDot} aria-hidden="true" />
            {eyebrow}
          </p>
        )}
        <div className={styles.titleRow}>
          {icon && (
            <span className={styles.iconTile}>
              <Icon name={icon} size={22} />
            </span>
          )}
          <div>
            <h1>{title}</h1>
            {subtitle && <p className={styles.subtitle}>{subtitle}</p>}
          </div>
        </div>
      </div>
      {actions && <div className={styles.actions}>{actions}</div>}
    </div>
  )
}
