// Hand-written from spring-petclinic-rest openapi.yml; replace with generated types later (ADR-0003).

export interface PetType {
  id: number
  name: string
}

export interface Visit {
  id: number
  /** ISO date, e.g. 2026-03-14. */
  date: string
  description: string
}

export interface Pet {
  id: number
  name: string
  /** ISO date, e.g. 2020-09-07. */
  birthDate: string
  type: PetType
  visits: Visit[]
}

export interface OwnerFields {
  firstName: string
  lastName: string
  address: string
  city: string
  telephone: string
}

export interface Owner extends OwnerFields {
  id: number
  pets: Pet[]
}

export interface OwnerPage {
  content: Owner[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export type OwnerSortField = 'id' | 'lastName'
export type SortDirection = 'asc' | 'desc'

export interface ListOwnersParams {
  lastName?: string
  /** The API defaults to id. */
  sort?: OwnerSortField
  /** The API defaults to asc. */
  direction?: SortDirection
  page?: number
  size?: number
}

export interface ProblemDetail {
  title?: string
  detail?: string
  status?: number
}
