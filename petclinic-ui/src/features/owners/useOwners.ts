import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { addOwner, listOwners } from '../../api/owners'
import type { ListOwnersParams } from '../../api/types'

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
