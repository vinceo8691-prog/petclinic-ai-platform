import '@testing-library/jest-dom/vitest'
import { afterEach, beforeEach, vi } from 'vitest'

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
