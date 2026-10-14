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

  it('does not nag about a field just because it was left, only after a submit attempt', async () => {
    renderApp('/owners/new')

    await userEvent.type(screen.getByLabelText('First name'), 'G3orge')
    await userEvent.tab()
    expect(screen.getByLabelText('First name')).toHaveAttribute('aria-invalid', 'false')

    await userEvent.click(screen.getByRole('button', { name: 'Save owner' }))

    const input = screen.getByLabelText('First name')
    expect(input).toHaveAttribute('aria-invalid', 'true')
    expect(input).toHaveAccessibleDescription(/First name can only contain letters/)

    // After a failed submit the error clears as soon as the value is fixed.
    await userEvent.clear(input)
    await userEvent.type(input, 'George')
    expect(input).toHaveAttribute('aria-invalid', 'false')
  })

  it.each(['5551234', '60855510231', '608-555-1023'])(
    'blocks the telephone "%s" because it is not exactly 10 digits, without calling the API',
    async (telephone) => {
      const fetchMock = vi.fn<typeof fetch>()
      vi.stubGlobal('fetch', fetchMock)
      renderApp('/owners/new')
      await fillValid()
      const field = screen.getByLabelText('Telephone')
      await userEvent.clear(field)
      await userEvent.type(field, telephone)

      await userEvent.click(screen.getByRole('button', { name: 'Save owner' }))

      expect(field).toHaveAttribute('aria-invalid', 'true')
      expect(field).toHaveAccessibleDescription(/Telephone must be exactly 10 digits/)
      expect(fetchMock).not.toHaveBeenCalled()
    },
  )

  it('tells the user the telephone is 10 digits before they type it', () => {
    renderApp('/owners/new')

    expect(screen.getByLabelText('Telephone')).toHaveAccessibleDescription('10 digits, no spaces or dashes.')
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

    expect(await screen.findByRole('heading', { name: 'Maria Lopez' })).toBeInTheDocument()
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

  describe('when the server rejects fields', () => {
    const rejection = (field: string, rejectedValue: string, defaultMessage: string) => ({
      title: 'Bad Request',
      status: 400,
      detail: 'The request contains invalid or missing parameters',
      schemaValidationErrors: [
        { message: `Field '${field}' ${defaultMessage} (rejected value: ${rejectedValue})`, field, rejectedValue, defaultMessage },
      ],
    })

    it('shows the message under the field and in a summary, never the raw rule', async () => {
      vi.stubGlobal(
        'fetch',
        vi.fn<typeof fetch>(() => jsonResponse(rejection('telephone', '6085550000', 'must match "^[0-9]{10}$"'), 400)),
      )
      renderApp('/owners/new')
      await fillValid()

      await userEvent.click(screen.getByRole('button', { name: 'Save owner' }))

      const summary = await screen.findByText('Some fields were not accepted. Please fix them below.')
      expect(summary.closest('[role="alert"]')).toHaveFocus()
      expect(screen.getByRole('link', { name: 'Telephone' })).toBeInTheDocument()
      const field = screen.getByLabelText('Telephone')
      expect(field).toHaveAttribute('aria-invalid', 'true')
      expect(field).toHaveAccessibleDescription(/Telephone is not valid\./)
      expect(document.body).not.toHaveTextContent('^[0-9]{10}$')
      // The server's general message is not repeated above the form.
      expect(screen.queryByText('Could not save owner')).not.toBeInTheDocument()
      expect(screen.getByLabelText('First name')).toHaveValue('Maria')
    })

    it('clears the message once the user changes that field', async () => {
      vi.stubGlobal('fetch', vi.fn<typeof fetch>(() => jsonResponse(rejection('city', 'Madison', 'must not be blank'), 400)))
      renderApp('/owners/new')
      await fillValid()
      await userEvent.click(screen.getByRole('button', { name: 'Save owner' }))
      expect(await screen.findByLabelText('City')).toHaveAccessibleDescription('City must not be blank.')

      await userEvent.type(screen.getByLabelText('City'), 'x')

      expect(screen.getByLabelText('City')).toHaveAttribute('aria-invalid', 'false')
      expect(screen.queryByText('Some fields were not accepted. Please fix them below.')).not.toBeInTheDocument()
    })

    it('falls back to the server message when it names no field the form has', async () => {
      vi.stubGlobal('fetch', vi.fn<typeof fetch>(() => jsonResponse(rejection('id', '3', 'must be null'), 400)))
      renderApp('/owners/new')
      await fillValid()

      await userEvent.click(screen.getByRole('button', { name: 'Save owner' }))

      expect(await screen.findByText('The request contains invalid or missing parameters')).toBeInTheDocument()
      expect(screen.getByText('Could not save owner')).toBeInTheDocument()
    })
  })

  it('says the user may not make the change when the server answers 403', async () => {
    vi.stubGlobal('fetch', vi.fn<typeof fetch>(() => jsonResponse({ detail: 'Access Denied' }, 403)))
    renderApp('/owners/new')
    await fillValid()

    await userEvent.click(screen.getByRole('button', { name: 'Save owner' }))

    expect(await screen.findByText("You don't have permission to make this change.")).toBeInTheDocument()
    expect(screen.queryByText("You don't have access to this information.")).not.toBeInTheDocument()
  })

  it('shows the server message for a conflict', async () => {
    vi.stubGlobal('fetch', vi.fn<typeof fetch>(() => jsonResponse({ detail: 'Owner already exists' }, 409)))
    renderApp('/owners/new')
    await fillValid()

    await userEvent.click(screen.getByRole('button', { name: 'Save owner' }))

    expect(await screen.findByText('Owner already exists')).toBeInTheDocument()
  })

  it('cancels back to the owners list', async () => {
    vi.stubGlobal('fetch', vi.fn<typeof fetch>(() => new Promise(() => {})))
    renderApp('/owners/new')

    await userEvent.click(screen.getByRole('link', { name: 'Cancel' }))

    expect(await screen.findByRole('heading', { name: 'Owners' })).toBeInTheDocument()
  })
})
