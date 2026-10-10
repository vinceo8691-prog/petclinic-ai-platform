import { Link, useNavigate } from 'react-router-dom'
import type { Owner } from '../../api/types'
import { Icon } from '../../components/Icon'
import { formatPhone, initials, splitPets, summarizePets } from '../../lib/format'
import ui from '../../components/ui.module.css'
import styles from './Owners.module.css'

function Headers() {
  return (
    <tr>
      <th scope="col">Name</th>
      <th scope="col" className={styles.colTelephone}>Telephone</th>
      <th scope="col" className={styles.colAddress}>Address</th>
      <th scope="col" className={styles.colCity}>City</th>
      <th scope="col" className={styles.colPets}>Pets</th>
      <th scope="col" className={styles.colActions}>Actions</th>
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
        {owners.map((owner) => {
          const { names, hiddenCount } = splitPets(owner.pets)
          const phone = formatPhone(owner.telephone)
          const fullName = `${owner.lastName}, ${owner.firstName}`
          const detailsPath = `/owners/${owner.id}`
          return (
            <tr
              key={owner.id}
              className={styles.row}
              // Mouse convenience only; the name link and View button are the accessible ways in.
              onClick={(event) => {
                if (!(event.target as HTMLElement).closest('a')) navigate(detailsPath)
              }}
            >
              <td>
                <div className={styles.nameCell}>
                  <span className={styles.avatar} aria-hidden="true">
                    {initials(owner.firstName, owner.lastName)}
                  </span>
                  <div>
                    <Link to={detailsPath} className={styles.name}>
                      {fullName}
                    </Link>
                    <span className={styles.metaAddress}>{owner.address}</span>
                    <span className={styles.metaAddressCity}>
                      {owner.address}, {owner.city}
                    </span>
                    <span className={styles.metaContact}>
                      {phone}
                      {owner.pets.length > 0 && ` · ${summarizePets(owner.pets)}`}
                    </span>
                  </div>
                </div>
              </td>
              <td className={`${styles.colTelephone} ${styles.nowrap}`}>{phone}</td>
              <td className={styles.colAddress}>{owner.address}</td>
              <td className={styles.colCity}>{owner.city}</td>
              <td className={styles.colPets}>
                {names.length > 0 ? (
                  <div className={styles.pills}>
                    {names.map((petName) => (
                      <span key={petName} className={styles.pill}>
                        {petName}
                      </span>
                    ))}
                    {hiddenCount > 0 && <span className={`${styles.pill} ${styles.pillMore}`}>+{hiddenCount}</span>}
                  </div>
                ) : (
                  <span className={styles.muted}>None</span>
                )}
              </td>
              <td className={styles.colActions}>
                <Link to={detailsPath} className={ui.iconAction} aria-label={`View ${fullName}`}>
                  <Icon name="eye" />
                </Link>
              </td>
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
          {Array.from({ length: SKELETON_ROWS }, (_, index) => (
            <tr key={index}>
              <td>
                <div className={styles.nameCell}>
                  <span className={styles.avatarBar} />
                  <span className={styles.bar} style={{ width: BAR_WIDTHS[0] }} />
                </div>
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
              <td className={styles.colActions}>
                <span className={styles.bar} style={{ width: 36 }} />
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
