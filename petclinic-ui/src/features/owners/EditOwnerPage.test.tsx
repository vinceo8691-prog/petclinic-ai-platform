import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { jsonResponse, makeOwner, renderApp } from '../../test/utils'

function mockApi(overrides: Record<string, () => Promise<Response>> = {}) {
  const mock = vi.fn<typeof fetch>((input, init) => {
    const url = String(input)
    const override = overrides[`${init?.method ?? 'GET'} ${url}`]
    if (override) return override()
    if (url.endsWith('/owners/1')) return jsonResponse(makeOwner(1))
    return jsonResponse({}, 404)
  })
  vi.stubGlobal('fetch', mock)
  return mock
}

const noContent = () => Promise.resolve(new Response(null, { status: 204 }))

describe('EditOwnerPage', () => {
  it('prefills the form with the current values', async () => {
    mockApi()
    renderApp('/owners/1/edit')

    expect(await screen.findByLabelText('First name')).toHaveValue('George')
    expect(screen.getByLabelText('Last name')).toHaveValue('Franklin')
    expect(screen.getByLabelText('Address')).toHaveValue('110 W. Liberty St.')
    expect(screen.getByLabelText('City')).toHaveValue('Madison')
    expect(screen.getByLabelText('Telephone')).toHaveValue('6085551023')
    expect(screen.getByRole('link', { name: /Owner details/ })).toHaveAttribute('href', '/owners/1')
  })

  it('PUTs the trimmed values, then shows the owner details', async () => {
    const fetchMock = mockApi({ 'PUT /petclinic/api/owners/1': noContent })
    renderApp('/owners/1/edit')

    const city = await screen.findByLabelText('City')
    await userEvent.clear(city)
    await userEvent.type(city, '  Verona ')
    await userEvent.click(screen.getByRole('button', { name: 'Save changes' }))

    expect(await screen.findByRole('heading', { name: 'George Franklin' })).toBeInTheDocument()
    const put = fetchMock.mock.calls.find(([, init]) => init?.method === 'PUT')!
    expect(put[0]).toBe('/petclinic/api/owners/1')
    expect(JSON.parse(String(put[1]?.body))).toEqual({
      firstName: 'George',
      lastName: 'Franklin',
      address: '110 W. Liberty St.',
      city: 'Verona',
      telephone: '6085551023',
    })
  })

  it('validates before sending anything', async () => {
    const fetchMock = mockApi()
    renderApp('/owners/1/edit')

    await userEvent.clear(await screen.findByLabelText('Last name'))
    await userEvent.click(screen.getByRole('button', { name: 'Save changes' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Last name is required.')
    expect(fetchMock.mock.calls.some(([, init]) => init?.method === 'PUT')).toBe(false)
  })

  it('keeps the form and shows the server message when saving fails', async () => {
    mockApi({ 'PUT /petclinic/api/owners/1': () => jsonResponse({ detail: 'Telephone is taken' }, 400) })
    renderApp('/owners/1/edit')

    await userEvent.click(await screen.findByRole('button', { name: 'Save changes' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Telephone is taken')
    expect(screen.getByLabelText('Last name')).toHaveValue('Franklin')
  })

  it('cancels back to the owner details without saving', async () => {
    const fetchMock = mockApi()
    renderApp('/owners/1/edit')

    await userEvent.click(await screen.findByRole('link', { name: 'Cancel' }))

    expect(await screen.findByRole('heading', { name: 'George Franklin' })).toBeInTheDocument()
    expect(fetchMock.mock.calls.some(([, init]) => init?.method === 'PUT')).toBe(false)
  })

  it('shows "not found" for an owner that does not exist', async () => {
    mockApi({ 'GET /petclinic/api/owners/1': () => jsonResponse({ detail: 'Owner not found' }, 404) })
    renderApp('/owners/1/edit')

    expect(await screen.findByRole('heading', { name: 'Owner not found' })).toBeInTheDocument()
  })
})
