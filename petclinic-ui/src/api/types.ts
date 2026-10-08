// Hand-written from spring-petclinic-rest openapi.yml; replace with generated types later (ADR-0003).

export interface Pet {
  id: number
  name: string
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

export interface ListOwnersParams {
  lastName?: string
  page?: number
  size?: number
}

export interface ProblemDetail {
  title?: string
  detail?: string
  status?: number
}
