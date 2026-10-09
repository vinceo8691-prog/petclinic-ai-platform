import { Link, useNavigate } from 'react-router-dom'
import type { Owner } from '../../api/types'
import { formatPhone, summarizePets } from '../../lib/format'
import styles from './Owners.module.css'

function Headers() {
  return (
    <tr>
      <th scope="col">Name</th>
      <th scope="col" className={styles.colTelephone}>Telephone</th>
      <th scope="col" className={styles.colAddress}>Address</th>
      <th scope="col" className={styles.colCity}>City</th>
      <th scope="col" className={styles.colPets}>Pets</th>
    </tr>
  )
}

export function OwnersTable({ owners }: { owners: Owner[] }) {
  const navigate = useNavigate()

  return (
    <table className={styles.table}>
      <caption className="visually-hidden">Pet owners</caption>
      <thead>
        <Headers />
      </thead>
      <tbody>
        {owners.map((o) => {
          const pets = summarizePets(o.pets)
          const phone = formatPhone(o.telephone)
          return (
            <tr
              key={o.id}
              className={styles.row}
              // Mouse convenience only; the name link is the accessible way in.
              onClick={(e) => {
                if (!(e.target as HTMLElement).closest('a')) navigate(`/owners/${o.id}`)
              }}
            >
              <td>
                <Link to={`/owners/${o.id}`} className={styles.name}>
                  {o.lastName}, {o.firstName}
                </Link>
                <span className={styles.metaAddress}>{o.address}</span>
                <span className={styles.metaAddressCity}>
                  {o.address}, {o.city}
                </span>
                <span className={styles.metaContact}>
                  {phone}
                  {pets && ` · ${pets}`}
                </span>
              </td>
              <td className={`${styles.colTelephone} ${styles.nowrap}`}>{phone}</td>
              <td className={styles.colAddress}>{o.address}</td>
              <td className={styles.colCity}>{o.city}</td>
              <td className={styles.colPets}>{pets || <span className={styles.muted}>None</span>}</td>
            </tr>
          )
        })}
      </tbody>
    </table>
  )
}

const SKELETON_ROWS = 8
const BAR_WIDTHS = [140, 100, 160, 90, 110]

export function OwnersTableSkeleton() {
  return (
    <div role="status">
      <span className="visually-hidden">Loading owners…</span>
      <table className={styles.table} aria-hidden="true">
        <thead>
          <Headers />
        </thead>
        <tbody>
          {Array.from({ length: SKELETON_ROWS }, (_, i) => (
            <tr key={i}>
              <td>
                <span className={styles.bar} style={{ width: BAR_WIDTHS[0] }} />
              </td>
              <td className={styles.colTelephone}>
                <span className={styles.bar} style={{ width: BAR_WIDTHS[1] }} />
              </td>
              <td className={styles.colAddress}>
                <span className={styles.bar} style={{ width: BAR_WIDTHS[2] }} />
              </td>
              <td className={styles.colCity}>
                <span className={styles.bar} style={{ width: BAR_WIDTHS[3] }} />
              </td>
              <td className={styles.colPets}>
                <span className={styles.bar} style={{ width: BAR_WIDTHS[4] }} />
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
