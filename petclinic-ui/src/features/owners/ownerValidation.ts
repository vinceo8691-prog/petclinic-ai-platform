import { serverFieldErrors, type ServerFieldError } from '../../api/client'
import type { OwnerFields } from '../../api/types'

// Mirrors the rules in spring-petclinic-rest openapi.yml (OwnerFields), including the telephone: exactly 10
// digits, as the Owner entity requires. The server remains authoritative.

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

// The telephone has no maximum length of its own: it must be exactly 10 digits.
const MAX_LENGTH: Record<Exclude<OwnerField, 'telephone'>, number> = {
  firstName: 30,
  lastName: 30,
  address: 255,
  city: 80,
}

const NAME_PATTERN = /^\p{L}+([ '-]\p{L}+){0,2}$/u
const LAST_NAME_PATTERN = /^\p{L}+([ '-]\p{L}+){0,2}\.?$/u
const TELEPHONE = /^[0-9]{10}$/

export function validateOwnerField(field: OwnerField, rawValue: string): string | undefined {
  const value = rawValue.trim()
  const label = LABELS[field]

  if (value === '') return `${label} is required.`
  if (field === 'telephone') {
    return TELEPHONE.test(value) ? undefined : `${label} must be exactly 10 digits, with no spaces or dashes.`
  }
  if (value.length > MAX_LENGTH[field]) return `${label} must be ${MAX_LENGTH[field]} characters or fewer.`

  if (field === 'firstName' && !NAME_PATTERN.test(value)) {
    return `${label} can only contain letters, with spaces, hyphens or apostrophes between words.`
  }
  if (field === 'lastName' && !LAST_NAME_PATTERN.test(value)) {
    return `${label} can only contain letters, with spaces, hyphens or apostrophes between words.`
  }
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

/** A server-rejected owner field: the wording to show, and the value that was rejected. */
export interface RejectedOwnerField {
  message: string
  rejectedValue: string
}

export type ServerOwnerErrors = Partial<Record<OwnerField, RejectedOwnerField>>

// Plain words and basic punctuation only, so a raw rule such as `must match "^[0-9]{10}$"` is never shown.
const PLAIN_SERVER_MESSAGE = /^[A-Za-z0-9 ,.'-]+$/

function wordServerError(field: OwnerField, serverError: ServerFieldError): string {
  // The form's own rule says it best. It can only differ from the server's when the two have drifted apart.
  const clientMessage = validateOwnerField(field, serverError.rejectedValue)
  if (clientMessage) return clientMessage
  if (PLAIN_SERVER_MESSAGE.test(serverError.message)) {
    const sentence = `${LABELS[field]} ${serverError.message}`
    return sentence.endsWith('.') ? sentence : `${sentence}.`
  }
  return `${LABELS[field]} is not valid.`
}

/** The owner fields the server rejected in a failed save, in friendly wording. Unknown fields are left out. */
export function ownerErrorsFromServer(error: unknown): ServerOwnerErrors {
  const errors: ServerOwnerErrors = {}
  for (const serverError of serverFieldErrors(error)) {
    const field = OWNER_FIELD_ORDER.find((candidate) => candidate === serverError.field)
    if (!field || errors[field]) continue
    errors[field] = { message: wordServerError(field, serverError), rejectedValue: serverError.rejectedValue }
  }
  return errors
}
