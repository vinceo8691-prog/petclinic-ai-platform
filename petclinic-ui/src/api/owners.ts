import { request } from './client'
import type { ListOwnersParams, Owner, OwnerFields, OwnerPage } from './types'

export function listOwners(params: ListOwnersParams = {}, signal?: AbortSignal): Promise<OwnerPage> {
  return request<OwnerPage>('/v2/owners', { params: { ...params }, signal })
}

export function addOwner(fields: OwnerFields): Promise<Owner> {
  return request<Owner>('/owners', { method: 'POST', body: fields })
}

export function getOwner(ownerId: number, signal?: AbortSignal): Promise<Owner> {
  return request<Owner>(`/owners/${ownerId}`, { signal })
}

/** The API answers 204 No Content, so callers refetch the owner to see the saved values. */
export function updateOwner(ownerId: number, fields: OwnerFields): Promise<void> {
  return request<void>(`/owners/${ownerId}`, { method: 'PUT', body: fields })
}

/** Deletes the owner and, through the API's cascade, their pets and visits. */
export function deleteOwner(ownerId: number): Promise<void> {
  return request<void>(`/owners/${ownerId}`, { method: 'DELETE' })
}
