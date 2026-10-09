import { Button } from './Button'
import { Icon } from './Icon'
import styles from './ui.module.css'

interface Props {
  title: string
  message?: string
  onRetry?: () => void
}

export function ErrorAlert({ title, message, onRetry }: Props) {
  return (
    <div role="alert" className={styles.alert}>
      <Icon name="alert" />
      <div className={styles.alertBody}>
        <p className={styles.alertTitle}>{title}</p>
        {message && <p>{message}</p>}
      </div>
      {onRetry && <Button onClick={onRetry}>Retry</Button>}
    </div>
  )
}
