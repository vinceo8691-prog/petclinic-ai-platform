import '@testing-library/jest-dom/vitest'
import { afterEach, beforeEach, vi } from 'vitest'

// jsdom does not implement the modal parts of <dialog>; this stand-in only tracks the open state.
HTMLDialogElement.prototype.showModal = function showModal() {
  this.setAttribute('open', '')
}
HTMLDialogElement.prototype.close = function close() {
  this.removeAttribute('open')
}

beforeEach(() => {
  try {
    localStorage.clear()
  } catch {
    // ignore
  }
})

afterEach(() => {
  vi.unstubAllGlobals()
})
