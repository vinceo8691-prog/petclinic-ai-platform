import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { jsonResponse, makeOwner, renderApp } from '../../test/utils'

async function fillValid() {
  await userEvent.type(screen.getByLabelText('First name'), 'Maria')
  await userEvent.type(screen.getByLabelText('Last name'), 'Lopez')
  await userEvent.type(screen.getByLabelText('Address'), '12 Oak St.')
  await userEvent.type(screen.getByLabelText('City'), 'Madison')
  await userEvent.type(screen.getByLabelText('Telephone'), '6085550000')
}

describe('AddOwnerPage', () => {
  it('is reachable from the Owners page', async () => {
    vi.stubGlobal('fetch', vi.fn<typeof fetch>(() => new Promise(() => {})))
    renderApp('/owners')

    await userEvent.click(screen.getByRole('link', { name: 'Add owner' }))

    expect(await screen.findByRole('heading', { name: 'Add owner' })).toBeInTheDocument()
    expect(document.title).toBe('Add owner – PetClinic')
  })

  it('shows a field error after the field is left', async () => {
    renderApp('/owners/new')

    await userEvent.type(screen.getByLabelText('First name'), 'G3orge')
    await userEvent.tab()

    const input = screen.getByLabelText('First name')
    expect(input).toHaveAttribute('aria-invalid', 'true')
    expect(screen.getByText(/First name can only contain letters/)).toBeInTheDocument()
    expect(input).toHaveAccessibleDescription(/First name can only contain letters/)
  })

  it('blocks submit, summarizes the errors and focuses the summary', async () => {
    const fetchMock = vi.fn<typeof fetch>()
    vi.stubGlobal('fetch', fetchMock)
    renderApp('/owners/new')

    await userEvent.click(screen.getByRole('button', { name: 'Save owner' }))

    const summary = await screen.findByText('Please fix the following:')
    expect(summary.closest('[role="alert"]')).toHaveFocus()
    expect(screen.getByRole('link', { name: 'Telephone' })).toBeInTheDocument()
    expect(fetchMock).not.toHaveBeenCalled()

    await userEvent.click(screen.getByRole('link', { name: 'City' }))
    expect(screen.getByLabelText('City')).toHaveFocus()
  })

  it('saves a valid owner and goes to their details', async () => {
    const fetchMock = vi.fn<typeof fetch>(() => jsonResponse(makeOwner(42, { firstName: 'Maria', lastName: 'Lopez' }), 201))
    vi.stubGlobal('fetch', fetchMock)
    renderApp('/owners/new')

    await fillValid()
    await userEvent.click(screen.getByRole('button', { name: 'Save owner' }))

    expect(await screen.findByText('Owner #42')).toBeInTheDocument()
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/petclinic/api/owners')
    expect(JSON.parse(String(init?.body))).toEqual({
      firstName: 'Maria',
      lastName: 'Lopez',
      address: '12 Oak St.',
      city: 'Madison',
      telephone: '6085550000',
    })
  })

  it('shows the server message and keeps the form values when saving fails', async () => {
    vi.stubGlobal('fetch', vi.fn<typeof fetch>(() => jsonResponse({ detail: 'Telephone already in use' }, 400)))
    renderApp('/owners/new')

    await fillValid()
    await userEvent.click(screen.getByRole('button', { name: 'Save owner' }))

    expect(await screen.findByText('Telephone already in use')).toBeInTheDocument()
    expect(screen.getByLabelText('First name')).toHaveValue('Maria')
    expect(screen.getByRole('button', { name: 'Save owner' })).toBeEnabled()
  })

  it('cancels back to the owners list', async () => {
    vi.stubGlobal('fetch', vi.fn<typeof fetch>(() => new Promise(() => {})))
    renderApp('/owners/new')

    await userEvent.click(screen.getByRole('link', { name: 'Cancel' }))

    expect(await screen.findByRole('heading', { name: 'Owners' })).toBeInTheDocument()
  })
})
