const CSRF_REJECTION = 'Invalid or missing CSRF token.'
const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS', 'TRACE'])

export class ApiError extends Error {
  constructor(status, detail = '') {
    super('Request failed with status ' + status)
    this.status = status
    this.detail = detail
    this.code = status === 403 && detail === CSRF_REJECTION ? 'csrf' : null
  }
}

export function apiErrorMessage(error, fallback) {
  if (error?.code === 'csrf') {
    return 'Your security token expired. Please try again.'
  }
  if (error?.status === 403) {
    return 'You do not have permission to perform this action.'
  }
  if (error?.status === 404) {
    return 'This item is unavailable. Refresh and try again.'
  }
  if (error?.status === 400 && error.detail === 'q must be at most 200 characters after stripping whitespace') {
    return 'Search text must be 200 characters or fewer.'
  }
  return fallback
}

export function createSessionClient({ onUnauthorized = () => {}, fetchImpl = (...args) => fetch(...args) } = {}) {
  let generation = 0
  let activeController

  function start() {
    const id = ++generation
    activeController?.abort()
    const controller = new AbortController()
    activeController = controller
    let csrf = null
    let pendingCsrf = null

    const isCurrent = () => id === generation

    function assertActive(signal = controller.signal) {
      if (!isCurrent() || signal.aborted) {
        throw new DOMException('This request is no longer active.', 'AbortError')
      }
    }

    async function send(path, { method, body, signal = controller.signal, requiresAuth, token }) {
      assertActive(signal)
      const headers = new Headers({ Accept: 'application/json, application/problem+json' })
      if (body !== undefined) {
        headers.set('Content-Type', 'application/json')
      }
      if (token) {
        headers.set(token.headerName, token.token)
      }

      let response
      try {
        response = await fetchImpl(path, {
          method,
          body: body === undefined ? undefined : JSON.stringify(body),
          headers,
          credentials: 'same-origin',
          cache: 'no-store',
          redirect: 'error',
          signal
        })
      } catch (error) {
        assertActive(signal)
        throw error
      }

      assertActive(signal)
      if (response.status === 401 && requiresAuth) {
        onUnauthorized(id)
        throw new ApiError(401)
      }
      if (response.status === 204) {
        return null
      }

      let data
      try {
        data = await response.json()
      } catch (error) {
        assertActive(signal)
        if (response.ok) {
          throw error
        }
      }

      // An abort can arrive after the response headers while the body is still being read.
      assertActive(signal)
      if (!response.ok) {
        const error = new ApiError(response.status, data?.detail)
        if (error.code === 'csrf' && csrf === token) {
          csrf = null
        }
        throw error
      }

      return data
    }

    async function getCsrf() {
      assertActive()
      if (csrf) {
        return csrf
      }
      if (!pendingCsrf) {
        pendingCsrf = send('/api/auth/csrf', { method: 'GET' })
          .then((token) => {
            assertActive()
            if (typeof token?.headerName !== 'string' || !token.headerName
                || typeof token.token !== 'string' || !token.token) {
              throw new Error('Invalid security token response.')
            }
            csrf = token
            return token
          })
          .finally(() => { pendingCsrf = null })
      }
      return pendingCsrf
    }

    async function request(path, { method = 'GET', body, signal, requiresAuth = false } = {}) {
      if (!path.startsWith('/api/')) {
        throw new TypeError('API requests must use a same-origin /api/ path.')
      }
      method = method.toUpperCase()
      const requestSignal = signal ? AbortSignal.any([controller.signal, signal]) : controller.signal
      assertActive(requestSignal)
      const token = SAFE_METHODS.has(method) ? null : await getCsrf()
      return send(path, { method, body, signal: requestSignal, requiresAuth, token })
    }

    return { id, isCurrent, request, getCsrf }
  }

  return { start }
}

