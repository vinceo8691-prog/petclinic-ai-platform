import styles from './Backdrop.module.css'

// First-pass illustrations, drawn as flat SVG shapes. Each is a self-contained piece so it can be swapped for
// real artwork later without touching the layout. All colors come from the artwork tokens in tokens.css.

const RICH = 'var(--color-teal-rich)'
const MID = 'var(--color-teal-mid)'
const LIGHT = 'var(--color-teal-light)'
const CLOUD = 'var(--color-cloud)'

function Cloud({ className }: { className: string }) {
  return (
    <svg className={className} viewBox="0 0 120 64" aria-hidden="true" focusable="false">
      <g fill={CLOUD}>
        <circle cx="34" cy="38" r="22" />
        <circle cx="62" cy="26" r="26" />
        <circle cx="90" cy="40" r="20" />
        <rect x="14" y="38" width="94" height="22" rx="11" />
      </g>
    </svg>
  )
}

/** Fixed layer behind everything: big teal blobs at the screen edges and a couple of clouds. */
export function Backdrop() {
  return (
    <div className={styles.backdrop} aria-hidden="true">
      <svg className={styles.blobLeft} viewBox="0 0 400 500" focusable="false">
        <path
          d="M120 20C220-20 360 40 380 160c20 120-50 170-40 250 10 70-90 110-190 90C50 480-20 400 10 290 40 180 20 60 120 20Z"
          fill={LIGHT}
        />
        <path
          d="M150 120c90-30 200 30 210 130 10 100-60 150-120 190-70 40-180 10-200-90-20-100 30-200 110-230Z"
          fill={RICH}
        />
      </svg>
      <svg className={styles.blobRight} viewBox="0 0 360 520" focusable="false">
        <path
          d="M240 10c80 50 120 150 100 240-20 90 20 160-40 230-60 60-190 40-260-30C-10 380 70 330 90 250 110 170 150-30 240 10Z"
          fill={MID}
        />
        <path
          d="M250 120c60 40 80 120 60 190-20 70 10 120-40 160-50 40-140 20-170-40-30-60 30-100 50-160 20-60 50-190 100-150Z"
          fill={RICH}
        />
      </svg>
      <Cloud className={styles.cloudTop} />
      <Cloud className={styles.cloudSide} />
    </div>
  )
}

function Leaves({ className }: { className: string }) {
  return (
    <svg className={className} viewBox="0 0 110 130" aria-hidden="true" focusable="false">
      <g fill={RICH}>
        <ellipse cx="55" cy="62" rx="12" ry="44" />
        <ellipse cx="30" cy="84" rx="10" ry="34" transform="rotate(-32 30 84)" />
        <ellipse cx="82" cy="80" rx="10" ry="36" transform="rotate(30 82 80)" />
      </g>
      <g fill={MID}>
        <ellipse cx="14" cy="104" rx="8" ry="24" transform="rotate(-58 14 104)" />
        <ellipse cx="98" cy="106" rx="8" ry="22" transform="rotate(56 98 106)" />
      </g>
    </svg>
  )
}

/** In-flow scene at the bottom of the page: a leaf cluster under each end of the card. */
export function PageScene() {
  return (
    <div className={styles.scene} aria-hidden="true">
      <Leaves className={styles.leavesLeft} />
      <Leaves className={styles.leavesRight} />
    </div>
  )
}
