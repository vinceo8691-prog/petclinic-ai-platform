import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { jsonResponse, makeOwner, makePage, makePet, renderApp } from '../../test/utils'

interface Call {
  url: string
  method: string
}

/** Serves owner 1 and records every request; `overrides` can replace the answer for a method + URL. */
function mockApi(overrides: Record<string, () => Promise<Response>> = {}) {
  const calls: Call[] = []
  const mock = vi.fn<typeof fetch>((input, init) => {
    const url = String(input)
    const method = init?.method ?? 'GET'
    calls.push({ url, method })
    const override = overrides[`${method} ${url}`]
    if (override) return override()
    if (url.endsWith('/owners/1')) return jsonResponse(makeOwner(1))
    if (url.includes('/v2/owners')) return jsonResponse(makePage())
    return jsonResponse({}, 404)
  })
  vi.stubGlobal('fetch', mock)
  return calls
}

const noContent = () => Promise.resolve(new Response(null, { status: 204 }))

describe('OwnerDetailsPage', () => {
  it('shows a loading state, then the owner, contact details and pets', async () => {
    mockApi()
    renderApp('/owners/1')

    expect(screen.getByRole('status')).toHaveTextContent('Loading owner…')
    expect(await screen.findByRole('heading', { name: 'George Franklin' })).toBeInTheDocument()
    expect(screen.getByText('110 W. Liberty St.')).toBeInTheDocument()
    expect(screen.getByText('Madison')).toBeInTheDocument()
    expect(screen.getByText('(608) 555-1023')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Pets (3)' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Leo' })).toBeInTheDocument()
    expect(document.title).toBe('George Franklin – PetClinic')
  })

  it('shows each pet with its type, birth date and visits', async () => {
    mockApi({
      'GET /petclinic/api/owners/1': () =>
        jsonResponse(
          makeOwner(1, {
            pets: [
              makePet(1, 'Leo', {
                birthDate: '2020-09-07',
                type: { id: 1, name: 'cat' },
                visits: [{ id: 5, date: '2026-03-14', description: 'rabies shot' }],
              }),
              makePet(2, 'Basil'),
            ],
          }),
        ),
    })
    renderApp('/owners/1')

    const visits = await screen.findByRole('list', { name: 'Visits for Leo' })
    expect(within(visits).getByText('Mar 14, 2026')).toBeInTheDocument()
    expect(within(visits).getByText(/rabies shot/)).toBeInTheDocument()
    expect(screen.getAllByText('Born Sep 7, 2020')).toHaveLength(2)
    expect(screen.getAllByText('cat')).toHaveLength(2)
    expect(screen.getByText('No visits recorded.')).toBeInTheDocument()
  })

  it('says so when the owner has no pets', async () => {
    mockApi({ 'GET /petclinic/api/owners/1': () => jsonResponse(makeOwner(1, { pets: [] })) })
    renderApp('/owners/1')

    expect(await screen.findByText('This owner has no pets yet.')).toBeInTheDocument()
  })

  it('links to the edit page and back to the list', async () => {
    mockApi()
    renderApp('/owners/1')

    expect(await screen.findByRole('link', { name: 'Edit owner' })).toHaveAttribute('href', '/owners/1/edit')
    const main = screen.getByRole('main')
    expect(within(main).getByRole('link', { name: 'Owners' })).toHaveAttribute('href', '/owners')
  })

  it('shows "not found" for an owner that does not exist', async () => {
    mockApi({ 'GET /petclinic/api/owners/1': () => jsonResponse({ detail: 'Owner not found' }, 404) })
    renderApp('/owners/1')

    expect(await screen.findByRole('heading', { name: 'Owner not found' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Back to owners' })).toHaveAttribute('href', '/owners')
  })

  it('does not call the API for an id that is not a number', async () => {
    const calls = mockApi()
    renderApp('/owners/abc')

    expect(await screen.findByRole('heading', { name: 'Owner not found' })).toBeInTheDocument()
    expect(calls).toHaveLength(0)
  })

  it('shows the server message and retries on error', async () => {
    let attempts = 0
    mockApi({
      'GET /petclinic/api/owners/1': () => (++attempts === 1 ? jsonResponse({ detail: 'boom' }, 500) : jsonResponse(makeOwner(1))),
    })
    renderApp('/owners/1')

    expect(await screen.findByRole('alert')).toHaveTextContent('boom')
    await userEvent.click(screen.getByRole('button', { name: 'Retry' }))
    expect(await screen.findByRole('heading', { name: 'George Franklin' })).toBeInTheDocument()
  })

  it('explains an access error without offering retry', async () => {
    mockApi({ 'GET /petclinic/api/owners/1': () => jsonResponse({ detail: 'Forbidden' }, 403) })
    renderApp('/owners/1')

    expect(await screen.findByRole('alert')).toHaveTextContent("You don't have access to this owner")
    expect(screen.queryByRole('button', { name: 'Retry' })).not.toBeInTheDocument()
  })
})

describe('deleting an owner', () => {
  async function openDeleteDialog() {
    await userEvent.click(await screen.findByRole('button', { name: 'Delete owner' }))
    return screen.getByRole('dialog', { name: 'Delete George Franklin?' })
  }

  it('asks for confirmation, naming what will be removed, and sends nothing yet', async () => {
    const calls = mockApi()
    renderApp('/owners/1')

    const dialog = await openDeleteDialog()

    expect(dialog).toHaveTextContent('3 pets and all of their visits')
    expect(calls.some((call) => call.method === 'DELETE')).toBe(false)
  })

  it('puts focus on Cancel so Enter cannot confirm by accident', async () => {
    mockApi()
    renderApp('/owners/1')

    const dialog = await openDeleteDialog()

    expect(within(dialog).getByRole('button', { name: 'Cancel' })).toHaveFocus()
  })

  it('closes on Cancel without deleting', async () => {
    const calls = mockApi()
    renderApp('/owners/1')
    const dialog = await openDeleteDialog()

    await userEvent.click(within(dialog).getByRole('button', { name: 'Cancel' }))

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(calls.some((call) => call.method === 'DELETE')).toBe(false)
  })

  it('returns focus to the Delete owner button when the dialog closes', async () => {
    mockApi()
    renderApp('/owners/1')
    const dialog = await openDeleteDialog()

    await userEvent.click(within(dialog).getByRole('button', { name: 'Cancel' }))

    expect(screen.getByRole('button', { name: 'Delete owner' })).toHaveFocus()
  })

  it('deletes the owner, returns to the list and says what happened', async () => {
    const calls = mockApi({ 'DELETE /petclinic/api/owners/1': noContent })
    renderApp('/owners/1')
    const dialog = await openDeleteDialog()

    await userEvent.click(within(dialog).getByRole('button', { name: 'Delete' }))

    expect(await screen.findByRole('heading', { name: 'Owners' })).toBeInTheDocument()
    expect(screen.getByText('Deleted owner George Franklin.')).toBeInTheDocument()
    expect(calls).toContainEqual({ url: '/petclinic/api/owners/1', method: 'DELETE' })
  })

  it('keeps the dialog open and shows the error when the delete fails', async () => {
    mockApi({ 'DELETE /petclinic/api/owners/1': () => jsonResponse({ detail: 'Could not delete' }, 500) })
    renderApp('/owners/1')
    const dialog = await openDeleteDialog()

    await userEvent.click(within(dialog).getByRole('button', { name: 'Delete' }))

    expect(await within(dialog).findByRole('alert')).toHaveTextContent('Could not delete')
    expect(screen.getByRole('dialog')).toBeInTheDocument()
    await waitFor(() => expect(within(dialog).getByRole('button', { name: 'Delete' })).toBeEnabled())
  })

  it('does not mention pets for an owner without any', async () => {
    mockApi({ 'GET /petclinic/api/owners/1': () => jsonResponse(makeOwner(1, { pets: [] })) })
    renderApp('/owners/1')

    const dialog = await openDeleteDialog()

    expect(dialog).not.toHaveTextContent('pets')
  })
})
