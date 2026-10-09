import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { AppRoutes } from '../AppRoutes'
import type { Owner, OwnerPage } from '../api/types'

export function renderApp(route = '/owners') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[route]}>
        <AppRoutes />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

export function jsonResponse(body: unknown, status = 200): Promise<Response> {
  return Promise.resolve(new Response(JSON.stringify(body), { status }))
}

export const makeOwner = (id: number, over: Partial<Owner> = {}): Owner => ({
  id,
  firstName: 'George',
  lastName: 'Franklin',
  address: '110 W. Liberty St.',
  city: 'Madison',
  telephone: '6085551023',
  pets: [
    { id: 1, name: 'Leo' },
    { id: 2, name: 'Basil' },
    { id: 3, name: 'Rosy' },
  ],
  ...over,
})

export const makePage = (over: Partial<OwnerPage> = {}): OwnerPage => ({
  content: [makeOwner(1)],
  page: 0,
  size: 20,
  totalElements: 45,
  totalPages: 3,
  ...over,
})

/** URLs requested from a fetch mock, as path + query. */
export function requestedUrls(mock: { mock: { calls: unknown[][] } }): string[] {
  return mock.mock.calls.map((c) => String(c[0]))
}
