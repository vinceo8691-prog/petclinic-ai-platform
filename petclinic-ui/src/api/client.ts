import type { ProblemDetail } from './types'

const BASE_URL = import.meta.env.VITE_PETCLINIC_API_URL ?? '/petclinic/api'

export class ApiError extends Error {
  status: number
  problem?: ProblemDetail

  constructor(status: number, message: string, problem?: ProblemDetail) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.problem = problem
  }
}

type Params = Record<string, string | number | undefined>

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  params?: Params
  body?: unknown
  signal?: AbortSignal
}

export async function request<T>(
  path: string,
  { method = 'GET', params = {}, body, signal }: RequestOptions = {},
): Promise<T> {
  const query = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== '') query.set(key, String(value))
  }
  const qs = query.toString()

  const headers: Record<string, string> = { Accept: 'application/json' }
  if (body !== undefined) headers['Content-Type'] = 'application/json'

  let response: Response
  try {
    response = await fetch(`${BASE_URL}${path}${qs ? `?${qs}` : ''}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      signal,
    })
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error
    throw new ApiError(0, 'Could not reach the server. Check your connection and try again.')
  }

  if (!response.ok) {
    const problem = (await response.json().catch(() => undefined)) as ProblemDetail | undefined
    throw new ApiError(
      response.status,
      problem?.detail ?? problem?.title ?? `Request failed with status ${response.status}`,
      problem,
    )
  }
  // 204 No Content (PUT, DELETE) has no body to parse.
  if (response.status === 204) return undefined as T
  return (await response.json()) as T
}

/** User-facing wording for a failed request. */
export function describeError(error: unknown): string {
  if (error instanceof ApiError && (error.status === 401 || error.status === 403)) {
    return "You don't have access to this information."
  }
  return error instanceof Error ? error.message : 'Something went wrong.'
}

export function isAccessError(error: unknown): boolean {
  return error instanceof ApiError && (error.status === 401 || error.status === 403)
}
