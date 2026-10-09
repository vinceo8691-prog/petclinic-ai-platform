import type { ComponentProps } from 'react'
import styles from './ui.module.css'

export type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger'

/** Class names for anything that should look like a button, e.g. a router <Link>. */
export function buttonClassName(variant: ButtonVariant = 'secondary'): string {
  return `${styles.button} ${styles[variant]}`
}

interface Props extends ComponentProps<'button'> {
  variant?: ButtonVariant
}

// React 19: `ref` is an ordinary prop, so it arrives through ...rest.
export function Button({ variant = 'secondary', type = 'button', className, ...rest }: Props) {
  return <button type={type} className={[buttonClassName(variant), className].filter(Boolean).join(' ')} {...rest} />
}
