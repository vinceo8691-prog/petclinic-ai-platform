import { QueryClient } from '@tanstack/react-query'
import { ApiError, shouldRetry } from './client'
import { getOwner } from './owners'
import { jsonResponse, makeOwner } from '../test/utils'

describe('shouldRetry', () => {
  it('retries network failures and server errors', () => {
    expect(shouldRetry(0, new ApiError(0, 'Could not reach the server.'))).toBe(true)
    expect(shouldRetry(1, new ApiError(500, 'boom'))).toBe(true)
    expect(shouldRetry(2, new ApiError(503, 'unavailable'))).toBe(true)
    expect(shouldRetry(0, new TypeError('unexpected'))).toBe(true)
  })

  it.each([400, 401, 403, 404, 409])('does not retry a %i, which a retry cannot fix', (status) => {
    expect(shouldRetry(0, new ApiError(status, 'nope'))).toBe(false)
  })

  it.each([408, 429])('still retries a %i (timeout, rate limit)', (status) => {
    expect(shouldRetry(0, new ApiError(status, 'try later'))).toBe(true)
  })

  it('gives up after three retries', () => {
    expect(shouldRetry(2, new ApiError(500, 'boom'))).toBe(true)
    expect(shouldRetry(3, new ApiError(500, 'boom'))).toBe(false)
  })
})

describe('shouldRetry with a real QueryClient', () => {
  function clientWithRetryRule() {
    return new QueryClient({ defaultOptions: { queries: { retry: shouldRetry, retryDelay: 0 } } })
  }

  it('asks the server once for an owner that does not exist', async () => {
    const fetchMock = vi.fn<typeof fetch>(() => jsonResponse({ detail: 'Owner not found' }, 404))
    vi.stubGlobal('fetch', fetchMock)

    await expect(
      clientWithRetryRule().fetchQuery({ queryKey: ['owner', 9], queryFn: () => getOwner(9) }),
    ).rejects.toMatchObject({ status: 404 })

    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('retries a server error three times, then reports it', async () => {
    const fetchMock = vi.fn<typeof fetch>(() => jsonResponse({ detail: 'boom' }, 500))
    vi.stubGlobal('fetch', fetchMock)

    await expect(
      clientWithRetryRule().fetchQuery({ queryKey: ['owner', 9], queryFn: () => getOwner(9) }),
    ).rejects.toMatchObject({ status: 500 })

    expect(fetchMock).toHaveBeenCalledTimes(4)
  })

  it('recovers when a retry succeeds', async () => {
    let calls = 0
    vi.stubGlobal(
      'fetch',
      vi.fn<typeof fetch>(() => (++calls === 1 ? jsonResponse({ detail: 'blip' }, 503) : jsonResponse(makeOwner(9)))),
    )

    const owner = await clientWithRetryRule().fetchQuery({ queryKey: ['owner', 9], queryFn: () => getOwner(9) })

    expect(owner.id).toBe(9)
    expect(calls).toBe(2)
  })
})
