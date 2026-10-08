import type { OwnerFields } from '../../api/types'

// Mirrors the rules in spring-petclinic-rest openapi.yml (OwnerFields). The server remains authoritative.

export type OwnerField = keyof OwnerFields
export type OwnerFormErrors = Partial<Record<OwnerField, string>>

export const OWNER_FIELD_ORDER: OwnerField[] = ['firstName', 'lastName', 'address', 'city', 'telephone']

const LABELS: Record<OwnerField, string> = {
  firstName: 'First name',
  lastName: 'Last name',
  address: 'Address',
  city: 'City',
  telephone: 'Telephone',
}

const MAX_LENGTH: Record<OwnerField, number> = {
  firstName: 30,
  lastName: 30,
  address: 255,
  city: 80,
  telephone: 20,
}

const NAME_PATTERN = /^\p{L}+([ '-]\p{L}+){0,2}$/u
const LAST_NAME_PATTERN = /^\p{L}+([ '-]\p{L}+){0,2}\.?$/u
const DIGITS = /^[0-9]*$/

export function validateOwnerField(field: OwnerField, rawValue: string): string | undefined {
  const value = rawValue.trim()
  const label = LABELS[field]

  if (value === '') return `${label} is required.`
  if (value.length > MAX_LENGTH[field]) return `${label} must be ${MAX_LENGTH[field]} characters or fewer.`

  if (field === 'firstName' && !NAME_PATTERN.test(value)) {
    return `${label} can only contain letters, with spaces, hyphens or apostrophes between words.`
  }
  if (field === 'lastName' && !LAST_NAME_PATTERN.test(value)) {
    return `${label} can only contain letters, with spaces, hyphens or apostrophes between words.`
  }
  if (field === 'telephone' && !DIGITS.test(value)) return `${label} must contain digits only.`
  return undefined
}

export function validateOwner(values: OwnerFields): OwnerFormErrors {
  const errors: OwnerFormErrors = {}
  for (const field of OWNER_FIELD_ORDER) {
    const message = validateOwnerField(field, values[field])
    if (message) errors[field] = message
  }
  return errors
}

export function trimOwner(values: OwnerFields): OwnerFields {
  return {
    firstName: values.firstName.trim(),
    lastName: values.lastName.trim(),
    address: values.address.trim(),
    city: values.city.trim(),
    telephone: values.telephone.trim(),
  }
}

export const FIELD_LABELS = LABELS
