const PATHS = {
  search: 'M7 12a5 5 0 1 0 0-10 5 5 0 0 0 0 10zm4-1 3.5 3.5',
  plus: 'M8 3v10M3 8h10',
  chevronLeft: 'M10 3 5 8l5 5',
  chevronRight: 'M6 3l5 5-5 5',
  alert: 'M8 2 1.5 13.5h13L8 2zM8 6.5v3.2M8 11.6v.2',
  close: 'M4 4l8 8M12 4l-8 8',
  chat: 'M2.5 3.5h11v7h-6L4.5 13v-2.5h-2v-7z',
  users: 'M6 7.6a2.4 2.4 0 1 0 0-4.8 2.4 2.4 0 0 0 0 4.8zM1.5 13.5c0-2.4 1.9-3.9 4.5-3.9s4.5 1.5 4.5 3.9M10.6 3a2.3 2.3 0 0 1 0 4.5M12.2 9.8c1.5.5 2.3 1.7 2.3 3.7',
  eye: 'M1.5 8s2.3-4.5 6.5-4.5S14.5 8 14.5 8s-2.3 4.5-6.5 4.5S1.5 8 1.5 8zM8 9.8a1.8 1.8 0 1 0 0-3.6 1.8 1.8 0 0 0 0 3.6z',
  edit: 'M11 2.5 13.5 5 5.5 13H3v-2.5L11 2.5zM9.5 4 12 6.5',
  trash: 'M2.5 4.5h11M6 4.5V3h4v1.5M4 4.5l.7 8.5h6.6l.7-8.5M6.7 7v4M9.3 7v4',
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
