import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderApp } from '../test/utils'

beforeEach(() => {
  vi.stubGlobal('fetch', vi.fn<typeof fetch>(() => new Promise(() => {})))
})

describe('AppShell', () => {
  it('shows the brand, the main navigation and a skip link', () => {
    renderApp()
    expect(screen.getByRole('link', { name: 'PetClinic' })).toHaveAttribute('href', '/owners')
    expect(screen.getByRole('link', { name: 'Owners' })).toHaveAttribute('aria-current', 'page')
    expect(screen.getByRole('link', { name: 'Skip to content' })).toHaveAttribute('href', '#main')
    expect(screen.getByRole('main')).toBeInTheDocument()
  })

  it('keeps Owners marked as current on child routes', () => {
    renderApp('/owners/new')
    const nav = screen.getByRole('navigation', { name: 'Main' })
    expect(within(nav).getByRole('link', { name: 'Owners' })).toHaveAttribute('aria-current', 'page')
  })

  it('redirects unknown paths to the owners list', () => {
    renderApp('/nowhere')
    expect(screen.getByRole('heading', { name: 'Owners' })).toBeInTheDocument()
  })
})

describe('Assistant panel', () => {
  it('starts as a floating launcher button with no panel, and the header has no assistant button', () => {
    renderApp()
    expect(screen.getByRole('button', { name: 'Assistant' })).toBeInTheDocument()
    expect(screen.queryByRole('complementary')).not.toBeInTheDocument()
    expect(within(screen.getByRole('banner')).queryByRole('button')).not.toBeInTheDocument()
  })

  it('opens the panel with focus on its heading and hides the launcher', async () => {
    renderApp()

    await userEvent.click(screen.getByRole('button', { name: 'Assistant' }))

    expect(screen.getByRole('complementary', { name: 'Assistant' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Assistant' })).toHaveFocus()
    expect(screen.getByText('Context: Owners')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Assistant' })).not.toBeInTheDocument()
  })

  it('closes with the X, brings the launcher back and focuses it', async () => {
    renderApp()
    await userEvent.click(screen.getByRole('button', { name: 'Assistant' }))

    await userEvent.click(screen.getByRole('button', { name: 'Close assistant' }))

    expect(screen.queryByRole('complementary')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Assistant' })).toHaveFocus()
  })

  it('closes with Escape and brings the launcher back', async () => {
    renderApp()
    await userEvent.click(screen.getByRole('button', { name: 'Assistant' }))

    await userEvent.keyboard('{Escape}')

    expect(screen.queryByRole('complementary')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Assistant' })).toHaveFocus()
  })

  it('can be reopened after closing', async () => {
    renderApp()
    await userEvent.click(screen.getByRole('button', { name: 'Assistant' }))
    await userEvent.click(screen.getByRole('button', { name: 'Close assistant' }))

    await userEvent.click(screen.getByRole('button', { name: 'Assistant' }))

    expect(screen.getByRole('complementary', { name: 'Assistant' })).toBeInTheDocument()
  })

  it('is a disabled preview that cannot send messages yet', async () => {
    renderApp()
    await userEvent.click(screen.getByRole('button', { name: 'Assistant' }))
    expect(screen.getByRole('textbox', { name: 'Message the assistant' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Send' })).toBeDisabled()
  })

  it('updates its context as the user navigates', async () => {
    renderApp()
    await userEvent.click(screen.getByRole('button', { name: 'Assistant' }))
    await userEvent.click(screen.getByRole('link', { name: 'Add owner' }))
    expect(await screen.findByText('Context: Add owner')).toBeInTheDocument()
  })

  it('remembers whether it was open', async () => {
    const first = renderApp()
    await userEvent.click(screen.getByRole('button', { name: 'Assistant' }))
    first.unmount()

    renderApp()
    expect(screen.getByRole('complementary', { name: 'Assistant' })).toBeInTheDocument()
  })
})
