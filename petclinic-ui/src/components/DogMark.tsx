import dogUrl from '../assets/dog.svg'

/** Brand mark (src/assets/dog.svg). Decorative; the wordmark next to it carries the name. */
export function DogMark({ height = 28 }: { height?: number }) {
  return <img src={dogUrl} alt="" height={height} style={{ width: 'auto', display: 'block' }} />
}
