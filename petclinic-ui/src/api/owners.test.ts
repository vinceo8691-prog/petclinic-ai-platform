import { ApiError } from './client'
import { addOwner, listOwners } from './owners'
import { jsonResponse, makeOwner, makePage } from '../test/utils'

describe('listOwners', () => {
  it('builds the query string and omits empty params', async () => {
    const fetchMock = vi.fn<typeof fetch>(() => jsonResponse(makePage()))
    vi.stubGlobal('fetch', fetchMock)
    await listOwners({ page: 2, size: 5, lastName: '' })
    expect(fetchMock.mock.calls[0][0]).toBe('/petclinic/api/v2/owners?page=2&size=5')
  })

  it('throws an ApiError carrying the server message', async () => {
    vi.stubGlobal('fetch', vi.fn<typeof fetch>(() => jsonResponse({ title: 'Bad', detail: 'nope' }, 400)))
    await expect(listOwners()).rejects.toMatchObject({ name: 'ApiError', status: 400, message: 'nope' })
  })

  it('turns a network failure into a friendly ApiError', async () => {
    vi.stubGlobal('fetch', vi.fn<typeof fetch>(() => Promise.reject(new TypeError('Failed to fetch'))))
    const error = await listOwners().catch((e) => e)
    expect(error).toBeInstanceOf(ApiError)
    expect(error.message).toMatch(/Could not reach the server/)
  })
})

describe('addOwner', () => {
  it('POSTs the fields as JSON', async () => {
    const fetchMock = vi.fn<typeof fetch>(() => jsonResponse(makeOwner(7), 201))
    vi.stubGlobal('fetch', fetchMock)
    const fields = { firstName: 'A', lastName: 'B', address: 'C', city: 'D', telephone: '1' }

    const owner = await addOwner(fields)

    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/petclinic/api/owners')
    expect(init?.method).toBe('POST')
    expect(JSON.parse(String(init?.body))).toEqual(fields)
    expect(new Headers(init?.headers).get('Content-Type')).toBe('application/json')
    expect(owner.id).toBe(7)
  })
})
