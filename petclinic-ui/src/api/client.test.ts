import { QueryClient } from '@tanstack/react-query'
import { ApiError, describeWriteError, serverFieldErrors, shouldRetry } from './client'
import { getOwner } from './owners'
import { jsonResponse, makeOwner } from '../test/utils'

describe('describeWriteError', () => {
  it.each([401, 403])('says the user may not change data on a %i', (status) => {
    expect(describeWriteError(new ApiError(status, 'Forbidden'))).toBe("You don't have permission to make this change.")
  })

  it('shows the server message for a conflict, so it is never reported as "not found"', () => {
    const message = 'The requested resource could not be processed due to a data constraint violation'
    expect(describeWriteError(new ApiError(409, message))).toBe(message)
  })

  it('keeps the network wording', () => {
    expect(describeWriteError(new ApiError(0, 'Could not reach the server. Check your connection and try again.'))).toBe(
      'Could not reach the server. Check your connection and try again.',
    )
  })
})

describe('serverFieldErrors', () => {
  const problem = {
    status: 400,
    detail: 'The request contains invalid or missing parameters',
    schemaValidationErrors: [
      {
        message: "Field 'telephone' must match \"^[0-9]{10}$\" (rejected value: 12345)",
        field: 'telephone',
        rejectedValue: '12345',
        defaultMessage: 'must match "^[0-9]{10}$"',
      },
    ],
  }

  it('reads the field, the rejected value and the reason from a 400', () => {
    expect(serverFieldErrors(new ApiError(400, problem.detail, problem))).toEqual([
      { field: 'telephone', rejectedValue: '12345', message: 'must match "^[0-9]{10}$"' },
    ])
  })

  it('returns nothing for other statuses, other errors or a body without field errors', () => {
    expect(serverFieldErrors(new ApiError(409, 'Conflict', problem))).toEqual([])
    expect(serverFieldErrors(new ApiError(400, 'Bad request'))).toEqual([])
    expect(serverFieldErrors(new ApiError(400, 'Bad request', { schemaValidationErrors: [] }))).toEqual([])
    expect(serverFieldErrors(new Error('boom'))).toEqual([])
    expect(serverFieldErrors(undefined)).toEqual([])
  })

  it('skips entries that do not name a field, such as those the spec validator produces', () => {
    const error = new ApiError(400, 'Bad request', {
      schemaValidationErrors: [{ message: "[Path '/lastName'] Instance type (null) does not match" }, { message: 'x', field: '' }],
    })
    expect(serverFieldErrors(error)).toEqual([])
  })
})

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
