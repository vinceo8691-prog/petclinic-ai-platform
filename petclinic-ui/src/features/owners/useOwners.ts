import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { addOwner, deleteOwner, getOwner, listOwners, updateOwner } from '../../api/owners'
import type { ListOwnersParams, OwnerFields } from '../../api/types'

export function useOwners(params: ListOwnersParams) {
  return useQuery({
    queryKey: ['owners', params],
    queryFn: ({ signal }) => listOwners(params, signal),
    placeholderData: keepPreviousData,
  })
}

export function useAddOwner() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: addOwner,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['owners'] }),
  })
}

export function useOwner(ownerId: number | undefined) {
  return useQuery({
    queryKey: ['owner', ownerId],
    queryFn: ({ signal }) => getOwner(ownerId!, signal),
    enabled: ownerId !== undefined,
  })
}

export function useUpdateOwner(ownerId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (fields: OwnerFields) => updateOwner(ownerId, fields),
    onSuccess: () =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: ['owner', ownerId] }),
        queryClient.invalidateQueries({ queryKey: ['owners'] }),
      ]),
  })
}

export function useDeleteOwner(ownerId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => deleteOwner(ownerId),
    onSuccess: () => {
      // The owner no longer exists, so drop its cached details instead of refetching them.
      queryClient.removeQueries({ queryKey: ['owner', ownerId] })
      return queryClient.invalidateQueries({ queryKey: ['owners'] })
    },
  })
}
