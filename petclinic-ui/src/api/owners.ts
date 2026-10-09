import { request } from './client'
import type { ListOwnersParams, Owner, OwnerFields, OwnerPage } from './types'

export function listOwners(params: ListOwnersParams = {}, signal?: AbortSignal): Promise<OwnerPage> {
  return request<OwnerPage>('/v2/owners', { params: { ...params }, signal })
}

export function addOwner(fields: OwnerFields): Promise<Owner> {
  return request<Owner>('/owners', { method: 'POST', body: fields })
}
