/** Formats exactly-10-digit numbers as (608) 555-1023; anything else is shown as stored. */
export function formatPhone(digits: string): string {
  return /^\d{10}$/.test(digits) ? `(${digits.slice(0, 3)}) ${digits.slice(3, 6)}-${digits.slice(6)}` : digits
}

/** The first `max` pet names, and how many more there are. */
export function splitPets(pets: { name: string }[], max = 2): { names: string[]; hiddenCount: number } {
  return {
    names: pets.slice(0, max).map((pet) => pet.name),
    hiddenCount: Math.max(pets.length - max, 0),
  }
}

/** "Leo, Basil +2": the first `max` names as one line, then a count of the rest. */
export function summarizePets(pets: { name: string }[], max = 2): string {
  const { names, hiddenCount } = splitPets(pets, max)
  const list = names.join(', ')
  return hiddenCount > 0 ? `${list} +${hiddenCount}` : list
}

/** Up to two uppercase initials for an avatar, e.g. "George Franklin" gives "GF". */
export function initials(firstName: string, lastName: string): string {
  return `${firstName.charAt(0)}${lastName.charAt(0)}`.toUpperCase()
}

/** "2020-09-07" as "Sep 7, 2020". Reads the date parts directly so the user's time zone cannot shift the day. */
export function formatDate(isoDate: string): string {
  const match = /^(\d{4})-(\d{2})-(\d{2})/.exec(isoDate)
  if (!match) return isoDate
  const [, year, month, day] = match
  return new Date(Date.UTC(Number(year), Number(month) - 1, Number(day))).toLocaleDateString('en-US', {
    dateStyle: 'medium',
    timeZone: 'UTC',
  })
}
