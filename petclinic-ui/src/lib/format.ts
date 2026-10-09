/** Formats exactly-10-digit numbers as (608) 555-1023; anything else is shown as stored. */
export function formatPhone(digits: string): string {
  return /^\d{10}$/.test(digits) ? `(${digits.slice(0, 3)}) ${digits.slice(3, 6)}-${digits.slice(6)}` : digits
}

/** "Leo, Basil +2": the first `max` names, then a count of the rest. */
export function summarizePets(pets: { name: string }[], max = 2): string {
  const names = pets.slice(0, max).map((p) => p.name).join(', ')
  return pets.length > max ? `${names} +${pets.length - max}` : names
}
