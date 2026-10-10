import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { jsonResponse, makeOwner, makePage, renderApp, requestedUrls } from '../../test/utils'

function mockFetch(handler: (url: string) => Promise<Response>) {
  const mock = vi.fn<typeof fetch>((input) => handler(String(input)))
  vi.stubGlobal('fetch', mock)
  return mock
}

// The list, plus the details of owner 1 for tests that open an owner.
const ownersAndOwnerOne = (url: string) =>
  url.endsWith('/owners/1') ? jsonResponse(makeOwner(1)) : jsonResponse(makePage())

describe('OwnersPage', () => {
  it('shows a loading state, then the owners', async () => {
    let resolve!: (r: Response) => void
    mockFetch(() => new Promise((r) => (resolve = r)))
    renderApp()

    expect(screen.getByRole('status')).toHaveTextContent('Loading owners…')
    resolve(new Response(JSON.stringify(makePage())))

    expect(await screen.findByRole('link', { name: 'Franklin, George' })).toBeInTheDocument()
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('renders owner rows with formatted phone, pet pills and a details link', async () => {
    mockFetch(() => jsonResponse(makePage()))
    renderApp()

    const link = await screen.findByRole('link', { name: 'Franklin, George' })
    expect(link).toHaveAttribute('href', '/owners/1')
    const row = link.closest('tr')!
    expect(within(row).getByText('(608) 555-1023')).toBeInTheDocument()
    expect(within(row).getByText('Leo')).toBeInTheDocument()
    expect(within(row).getByText('Basil')).toBeInTheDocument()
    expect(within(row).getByText('+1')).toBeInTheDocument()
    expect(within(row).queryByText('Rosy')).not.toBeInTheDocument()
    expect(within(row).getByText('Madison')).toBeInTheDocument()
    expect(screen.getByText('45 owners')).toBeInTheDocument()
    expect(screen.getByText('Showing 1–20 of 45 owners')).toBeInTheDocument()
  })

  it('gives each row a single View action that opens the owner', async () => {
    mockFetch(ownersAndOwnerOne)
    renderApp()

    const viewLink = await screen.findByRole('link', { name: 'View Franklin, George' })
    expect(viewLink).toHaveAttribute('href', '/owners/1')
    expect(screen.queryByRole('button', { name: /delete/i })).not.toBeInTheDocument()

    await userEvent.click(viewLink)
    expect(await screen.findByRole('heading', { name: 'George Franklin' })).toBeInTheDocument()
  })

  it('shows the owner initials as a decorative avatar', async () => {
    mockFetch(() => jsonResponse(makePage()))
    renderApp()

    const row = (await screen.findByRole('link', { name: 'Franklin, George' })).closest('tr')!
    expect(within(row).getByText('GF')).toHaveAttribute('aria-hidden', 'true')
  })

  it('labels the page with an eyebrow and a title', async () => {
    mockFetch(() => jsonResponse(makePage()))
    renderApp()

    expect(screen.getByText('Clinic administration')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Owners' })).toBeInTheDocument()
    await screen.findByRole('link', { name: 'Franklin, George' })
  })

  it('shows "None" for an owner without pets', async () => {
    mockFetch(() => jsonResponse(makePage({ content: [makeOwner(1, { pets: [] })], totalElements: 1, totalPages: 1 })))
    renderApp()
    expect(await screen.findByText('None')).toBeInTheDocument()
  })

  it('opens the owner when the row is clicked', async () => {
    mockFetch(ownersAndOwnerOne)
    renderApp()
    const link = await screen.findByRole('link', { name: 'Franklin, George' })

    await userEvent.click(within(link.closest('tr')!).getByText('Madison'))

    expect(await screen.findByRole('heading', { name: 'George Franklin' })).toBeInTheDocument()
  })

  it('shows the server message and retries on error', async () => {
    let calls = 0
    mockFetch(() => (++calls === 1 ? jsonResponse({ detail: 'boom' }, 500) : jsonResponse(makePage())))
    renderApp()

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Could not load owners')
    expect(alert).toHaveTextContent('boom')

    await userEvent.click(screen.getByRole('button', { name: 'Retry' }))
    expect(await screen.findByRole('link', { name: 'Franklin, George' })).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('keeps the rows and says the refresh failed when a later refetch fails', async () => {
    let calls = 0
    mockFetch(() =>
      ++calls === 1 ? jsonResponse(makePage()) : Promise.reject(new TypeError('Failed to fetch')),
    )
    renderApp()
    await screen.findByRole('link', { name: 'Franklin, George' })

    // Going offline and back online triggers a refetch of the (stale) list, which now fails.
    window.dispatchEvent(new Event('offline'))
    window.dispatchEvent(new Event('online'))

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Could not refresh owners')
    expect(alert).toHaveTextContent('Showing the last results that loaded.')
    expect(alert).not.toHaveTextContent('Could not load owners')
    expect(screen.getByRole('link', { name: 'Franklin, George' })).toBeInTheDocument()
  })

  it('explains an access error without offering retry', async () => {
    mockFetch(() => jsonResponse({ detail: 'Forbidden' }, 403))
    renderApp()

    expect(await screen.findByRole('alert')).toHaveTextContent("You don't have access to owners")
    expect(screen.queryByRole('button', { name: 'Retry' })).not.toBeInTheDocument()
  })

  it('shows an empty state with an Add owner action when there are no owners', async () => {
    mockFetch(() => jsonResponse(makePage({ content: [], totalElements: 0, totalPages: 0 })))
    renderApp()

    expect(await screen.findByRole('heading', { name: 'No owners yet' })).toBeInTheDocument()
    expect(screen.getAllByRole('link', { name: 'Add owner' })).toHaveLength(2) // header action + empty state
  })

  it('searches by last name after a pause and resets to page 1', async () => {
    const fetchMock = mockFetch(() => jsonResponse(makePage()))
    renderApp('/owners?page=2')
    await screen.findByRole('link', { name: 'Franklin, George' })

    await userEvent.type(screen.getByRole('searchbox', { name: 'Search by last name' }), 'dav')

    await waitFor(() =>
      expect(requestedUrls(fetchMock).at(-1)).toBe('/petclinic/api/v2/owners?lastName=dav&page=0&size=20'),
    )
  })

  it('shows a no-results state and can return to the full list', async () => {
    const fetchMock = mockFetch((url) =>
      url.includes('lastName=zzz')
        ? jsonResponse(makePage({ content: [], totalElements: 0, totalPages: 0 }))
        : jsonResponse(makePage()),
    )
    renderApp('/owners?lastName=zzz')

    expect(await screen.findByRole('heading', { name: 'No owners found' })).toBeInTheDocument()
    expect(screen.getByText(/starting with “zzz”/)).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Show all owners' }))

    expect(await screen.findByRole('link', { name: 'Franklin, George' })).toBeInTheDocument()
    expect(requestedUrls(fetchMock).at(-1)).not.toContain('lastName')
    expect(screen.getByRole('searchbox')).toHaveValue('')
  })

  it('reads search, page and size from the URL', async () => {
    const fetchMock = mockFetch(() => jsonResponse(makePage({ page: 1, size: 10 })))
    renderApp('/owners?lastName=Davis&page=1&size=10')

    await screen.findByRole('link', { name: 'Franklin, George' })
    expect(requestedUrls(fetchMock)[0]).toBe('/petclinic/api/v2/owners?lastName=Davis&page=1&size=10')
    expect(screen.getByRole('searchbox')).toHaveValue('Davis')
    expect(screen.getByText('45 owners matching “Davis”')).toBeInTheDocument()
  })

  it('pages forward and back', async () => {
    const fetchMock = mockFetch(() => jsonResponse(makePage()))
    renderApp()
    await screen.findByRole('link', { name: 'Franklin, George' })
    expect(screen.getByRole('button', { name: /Previous/ })).toBeDisabled()

    await userEvent.click(screen.getByRole('button', { name: /Next/ }))
    await waitFor(() => expect(requestedUrls(fetchMock).at(-1)).toContain('page=1'))
    await userEvent.click(await screen.findByRole('button', { name: /Previous/ }))
    await waitFor(() => expect(requestedUrls(fetchMock).at(-1)).toContain('page=0'))
  })

  it('clears the search with the clear button in the field', async () => {
    const fetchMock = mockFetch(() => jsonResponse(makePage()))
    renderApp('/owners?lastName=Davis')
    await screen.findByRole('link', { name: 'Franklin, George' })

    await userEvent.click(screen.getByRole('button', { name: 'Clear search' }))

    expect(screen.getByRole('searchbox')).toHaveValue('')
    await waitFor(() => expect(requestedUrls(fetchMock).at(-1)).not.toContain('lastName'))
  })

  it('changes the page size and returns to page 1', async () => {
    const fetchMock = mockFetch(() => jsonResponse(makePage()))
    renderApp('/owners?page=2')
    await screen.findByRole('link', { name: 'Franklin, George' })

    await userEvent.selectOptions(screen.getByLabelText('Rows per page'), '50')

    await waitFor(() => {
      const last = requestedUrls(fetchMock).at(-1)!
      expect(last).toContain('size=50')
      expect(last).toContain('page=0')
    })
  })

  it('offers a way back when the page is past the end', async () => {
    mockFetch(() => jsonResponse(makePage({ content: [], page: 9, totalElements: 45, totalPages: 3 })))
    renderApp('/owners?page=9')

    expect(await screen.findByRole('heading', { name: 'This page is empty' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Go to first page' })).toBeInTheDocument()
  })

  describe('sorting by name', () => {
    const nameButton = () => screen.getByRole('button', { name: /^Name/ })
    const nameHeader = () => screen.getByRole('columnheader', { name: /^Name/ })

    it('shows the default order with a Name button that has not sorted yet', async () => {
      const fetchMock = mockFetch(() => jsonResponse(makePage()))
      renderApp()
      await screen.findByRole('link', { name: 'Franklin, George' })

      expect(nameHeader()).toHaveAttribute('aria-sort', 'none')
      expect(nameButton()).toHaveAccessibleName('Name, sort A to Z')
      expect(requestedUrls(fetchMock)[0]).not.toContain('sort')
    })

    it('cycles A to Z, Z to A, then back to the default order', async () => {
      const fetchMock = mockFetch(() => jsonResponse(makePage()))
      renderApp()
      await screen.findByRole('link', { name: 'Franklin, George' })

      await userEvent.click(nameButton())
      await waitFor(() => expect(requestedUrls(fetchMock).at(-1)).toContain('sort=lastName&direction=asc'))
      expect(nameHeader()).toHaveAttribute('aria-sort', 'ascending')
      expect(nameButton()).toHaveAccessibleName('Name, sorted A to Z, activate to sort Z to A')

      await userEvent.click(nameButton())
      await waitFor(() => expect(requestedUrls(fetchMock).at(-1)).toContain('sort=lastName&direction=desc'))
      expect(nameHeader()).toHaveAttribute('aria-sort', 'descending')
      expect(nameButton()).toHaveAccessibleName('Name, sorted Z to A, activate to clear the sorting')

      await userEvent.click(nameButton())
      await waitFor(() => expect(requestedUrls(fetchMock).at(-1)).not.toContain('sort='))
      expect(requestedUrls(fetchMock).at(-1)).not.toContain('direction=')
      expect(nameHeader()).toHaveAttribute('aria-sort', 'none')
    })

    it('goes back to page 1 whenever the sort changes', async () => {
      const fetchMock = mockFetch(() => jsonResponse(makePage()))
      renderApp('/owners?page=2')
      await screen.findByRole('link', { name: 'Franklin, George' })

      await userEvent.click(nameButton())

      await waitFor(() => expect(requestedUrls(fetchMock).at(-1)).toContain('page=0'))
      expect(requestedUrls(fetchMock).at(-1)).toContain('sort=lastName&direction=asc')
    })

    it('keeps the search text when sorting', async () => {
      const fetchMock = mockFetch(() => jsonResponse(makePage()))
      renderApp('/owners?lastName=Dav')
      await screen.findByRole('link', { name: 'Franklin, George' })

      await userEvent.click(nameButton())

      await waitFor(() => expect(requestedUrls(fetchMock).at(-1)).toContain('lastName=Dav'))
      expect(requestedUrls(fetchMock).at(-1)).toContain('sort=lastName&direction=asc')
      expect(screen.getByRole('searchbox')).toHaveValue('Dav')
    })

    it('reads the sort from the URL, so a reload or a shared link keeps it', async () => {
      const fetchMock = mockFetch(() => jsonResponse(makePage()))
      renderApp('/owners?sort=lastName&direction=desc')
      await screen.findByRole('link', { name: 'Franklin, George' })

      expect(requestedUrls(fetchMock)[0]).toBe(
        '/petclinic/api/v2/owners?page=0&size=20&sort=lastName&direction=desc',
      )
      expect(nameHeader()).toHaveAttribute('aria-sort', 'descending')
    })

    it('ignores sort values it does not offer instead of sending them to the API', async () => {
      const fetchMock = mockFetch(() => jsonResponse(makePage()))
      renderApp('/owners?sort=firstName&direction=desc')
      await screen.findByRole('link', { name: 'Franklin, George' })

      expect(requestedUrls(fetchMock)[0]).toBe('/petclinic/api/v2/owners?page=0&size=20')
      expect(nameHeader()).toHaveAttribute('aria-sort', 'none')
    })

    it('treats an unknown direction as ascending', async () => {
      const fetchMock = mockFetch(() => jsonResponse(makePage()))
      renderApp('/owners?sort=lastName&direction=sideways')
      await screen.findByRole('link', { name: 'Franklin, George' })

      expect(requestedUrls(fetchMock)[0]).toContain('sort=lastName&direction=asc')
    })

    it('shows plain Name text, not a button, while the first load is in progress', () => {
      mockFetch(() => new Promise(() => {}))
      renderApp()

      expect(screen.queryByRole('button', { name: /^Name/ })).not.toBeInTheDocument()
    })
  })

  it('sets the document title', async () => {
    mockFetch(() => jsonResponse(makePage()))
    renderApp()
    await screen.findByRole('link', { name: 'Franklin, George' })
    expect(document.title).toBe('Owners – PetClinic')
  })
})
