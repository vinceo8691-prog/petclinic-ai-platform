// Friendly names over the types generated from spring-petclinic-rest openapi.yml (ADR-0003).
// Regenerate schema.d.ts with `npm run api:generate` whenever the spec changes; CI fails if it is stale.

import type { components, operations } from './schema'

type Schemas = components['schemas']

/** Makes optional properties required, for fields the spec leaves optional but the API always sends. */
type WithRequired<Type, Key extends keyof Type> = Type & { [Property in Key]-?: Type[Property] }

export type PetType = Schemas['PetType']

export type Visit = Schemas['Visit']

export type Pet = Schemas['Pet']

export type OwnerFields = Schemas['OwnerFields']

// The spec leaves Owner.id optional (readOnly, so it is not sent on create), but every owner the API
// returns has one.
export type Owner = WithRequired<Schemas['Owner'], 'id'>

export type OwnerPage = Omit<Schemas['OwnerPage'], 'content'> & { content: Owner[] }

export type ListOwnersParams = NonNullable<operations['listOwnersPage']['parameters']['query']>

export type OwnerSortField = NonNullable<ListOwnersParams['sort']>
export type SortDirection = NonNullable<ListOwnersParams['direction']>

// The spec lists every ProblemDetail field as required, but error bodies from the container or the
// gateway may omit them, so the UI treats them all as optional.
export type ProblemDetail = Partial<Schemas['ProblemDetail']>
