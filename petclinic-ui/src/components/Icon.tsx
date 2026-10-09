const PATHS = {
  search: 'M7 12a5 5 0 1 0 0-10 5 5 0 0 0 0 10zm4-1 3.5 3.5',
  plus: 'M8 3v10M3 8h10',
  chevronLeft: 'M10 3 5 8l5 5',
  chevronRight: 'M6 3l5 5-5 5',
  alert: 'M8 2 1.5 13.5h13L8 2zM8 6.5v3.2M8 11.6v.2',
  close: 'M4 4l8 8M12 4l-8 8',
  chat: 'M2.5 3.5h11v7h-6L4.5 13v-2.5h-2v-7z',
} as const

export type IconName = keyof typeof PATHS

/** 16px stroke icon. Decorative: pair with visible text or give the button an aria-label. */
export function Icon({ name, size = 16 }: { name: IconName; size?: number }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 16 16"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.5"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
    >
      <path d={PATHS[name]} />
    </svg>
  )
}
