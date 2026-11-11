import assert from 'node:assert/strict'
import { test } from 'node:test'
import { ApiError, createSessionClient } from '../src/sessionApi.js'

function deferred() {
  let resolve
  let reject
  const promise = new Promise((yes, no) => {
    resolve = yes
    reject = no
  })
  return { promise, resolve, reject }
}

function harness(onUnauthorized) {
  const calls = []
  const client = createSessionClient({
    onUnauthorized,
    fetchImpl: (path, options) => {
      const pending = deferred()
      calls.push({ path, ...options, ...pending })
      return pending.promise
    }
  })
  const turn = () => new Promise((resolve) => setImmediate(resolve))
  const reply = async (index, body, status = 200) => {
    calls[index].resolve(status === 204
      ? new Response(null, { status })
      : Response.json(body, { status }))
    await turn()
  }
  return { client, calls, reply, turn }
}

const csrf = (token) => ({ headerName: 'X-CSRF-TOKEN', token })
const invalidCsrf = { detail: 'Invalid or missing CSRF token.' }

test('ordinary GET sends same-origin cookies and does not fetch CSRF', async () => {
  const h = harness()
  const request = h.client.start().request('/api/lists', { requiresAuth: true })
  assert.equal(h.calls.length, 1)
  assert.equal(h.calls[0].credentials, 'same-origin')
  assert.equal(h.calls[0].cache, 'no-store')
  assert.equal(h.calls[0].headers.has('X-CSRF-TOKEN'), false)
  await h.reply(0, [{ id: 1 }])
  assert.deepEqual(await request, [{ id: 1 }])
})

test('concurrent writes share the token bootstrap and 204 needs no JSON', async () => {
  const h = harness()
  const session = h.client.start()
  const post = session.request('/api/reminders', { method: 'POST', body: { title: 'A' } })
  const put = session.request('/api/reminders/1', { method: 'PUT', body: { title: 'B' } })
  const patch = session.request('/api/reminders/1/completion', { method: 'PATCH', body: { completed: true } })
  const remove = session.request('/api/lists/2', { method: 'DELETE' })
  assert.equal(h.calls.length, 1)
  assert.equal(h.calls[0].path, '/api/auth/csrf')
  await h.reply(0, csrf('fixture-token'))
  assert.equal(h.calls.length, 5)
  assert.deepEqual(h.calls.slice(1).map(({ method }) => method), ['POST', 'PUT', 'PATCH', 'DELETE'])
  for (const call of h.calls.slice(1)) {
    assert.equal(call.headers.get('X-CSRF-TOKEN'), 'fixture-token')
    assert.equal(call.credentials, 'same-origin')
  }
  assert.equal(h.calls[1].body, '{"title":"A"}')
  assert.equal(h.calls[1].headers.get('Content-Type'), 'application/json')
  await h.reply(1, { id: 1 }, 201)
  await h.reply(2, { id: 1 })
  await h.reply(3, { id: 1 })
  await h.reply(4, null, 204)
  assert.deepEqual(await Promise.all([post, put, patch, remove]), [{ id: 1 }, { id: 1 }, { id: 1 }, null])
})

test('a new session aborts old requests and loads its own token', async () => {
  const h = harness()
  const beforeLogin = h.client.start()
  const login = beforeLogin.request('/api/auth/login', { method: 'POST', body: { email: 'alice@example.com' } })
  await h.reply(0, csrf('anonymous-fixture'))
  assert.equal(h.calls[1].headers.get('X-CSRF-TOKEN'), 'anonymous-fixture')
  await h.reply(1, { id: 1 })
  await login

  const alice = h.client.start()
  assert.equal(beforeLogin.isCurrent(), false)
  assert.equal(h.calls[1].signal.aborted, true)
  const token = alice.getCsrf()
  await h.reply(2, csrf('alice-fixture'))
  await token
  const logout = alice.request('/api/auth/logout', { method: 'POST', requiresAuth: true })
  await h.turn()
  assert.equal(h.calls[3].headers.get('X-CSRF-TOKEN'), 'alice-fixture')
  await h.reply(3, null, 204)
  await logout

  const bob = h.client.start()
  assert.equal(alice.isCurrent(), false)
  assert.notEqual(bob.id, alice.id)
  const next = bob.getCsrf()
  await h.reply(4, csrf('bob-fixture'))
  assert.deepEqual(await next, csrf('bob-fixture'))
})

test('old successful response and old 401 cannot affect the new user', async () => {
  const unauthorized = []
  const h = harness((id) => unauthorized.push(id))
  const alice = h.client.start()
  const oldPage = alice.request('/api/reminders/page', { requiresAuth: true })
  const oldCounts = alice.request('/api/reminders/counts', { requiresAuth: true })
  const stalePage = assert.rejects(oldPage, { name: 'AbortError' })
  const staleCounts = assert.rejects(oldCounts, { name: 'AbortError' })
  const bob = h.client.start()
  const newPage = bob.request('/api/reminders/page', { requiresAuth: true })
  await h.reply(2, { content: [{ title: 'Bob only' }] })
  await h.reply(0, { content: [{ title: 'Alice only' }] })
  await h.reply(1, { detail: 'Authentication is required.' }, 401)
  await Promise.all([stalePage, staleCounts])
  assert.deepEqual(await newPage, { content: [{ title: 'Bob only' }] })
  assert.deepEqual(unauthorized, [])
})

test('a response body that finishes after logout is stale even if headers arrived first', async () => {
  const h = harness()
  const alice = h.client.start()
  const body = deferred()
  const result = alice.request('/api/lists')
  const rejected = assert.rejects(result, { name: 'AbortError' })
  h.calls[0].resolve({ ok: true, status: 200, json: () => body.promise })
  await h.turn()
  h.client.start()
  body.resolve([{ name: 'Alice only' }])
  await rejected
})

test('a write waiting for an old session token is never sent in the new session', async () => {
  const h = harness()
  const alice = h.client.start()
  const oldWrite = alice.request('/api/reminders', { method: 'POST', body: { title: 'Alice draft' } })
  const rejected = assert.rejects(oldWrite, { name: 'AbortError' })
  assert.equal(h.calls[0].path, '/api/auth/csrf')

  const bob = h.client.start()
  const newWrite = bob.request('/api/reminders', { method: 'POST', body: { title: 'Bob draft' } })
  await h.reply(0, csrf('alice-fixture'))
  await rejected
  assert.equal(h.calls.length, 2)
  await h.reply(1, csrf('bob-fixture'))
  assert.equal(h.calls[2].body, '{"title":"Bob draft"}')
  assert.equal(h.calls[2].headers.get('X-CSRF-TOKEN'), 'bob-fixture')
  await h.reply(2, { id: 2 }, 201)
  await newWrite
})

test('a cancelled page cannot commit a delayed body in the same session', async () => {
  const h = harness()
  const session = h.client.start()
  const page = new AbortController()
  const body = deferred()
  const result = session.request('/api/reminders/page', { signal: page.signal, requiresAuth: true })
  const rejected = assert.rejects(result, { name: 'AbortError' })
  h.calls[0].resolve({ ok: true, status: 200, json: () => body.promise })
  await h.turn()
  page.abort()
  assert.equal(h.calls[0].signal.aborted, true)
  assert.equal(session.isCurrent(), true)
  body.resolve({ content: [] })
  await rejected
})

test('only protected 401 notifies session expiry', async () => {
  const unauthorized = []
  const h = harness((id) => unauthorized.push(id))
  const session = h.client.start()
  const login = session.request('/api/auth/login', { method: 'POST' })
  const wrongCredentials = assert.rejects(login, { status: 401 })
  await h.reply(0, csrf('fixture-token'))
  await h.reply(1, { detail: 'Invalid email or password.' }, 401)
  await wrongCredentials
  assert.deepEqual(unauthorized, [])

  const page = session.request('/api/reminders/page', { requiresAuth: true })
  const expired = assert.rejects(page, { status: 401 })
  await h.reply(2, { detail: 'Authentication is required.' }, 401)
  await expired
  assert.deepEqual(unauthorized, [session.id])
})

test('CSRF rejection refreshes on the next deliberate write without replaying the first', async () => {
  const unauthorized = []
  const h = harness((id) => unauthorized.push(id))
  const session = h.client.start()
  const first = session.request('/api/reminders', { method: 'POST', requiresAuth: true })
  const rejected = assert.rejects(first, { status: 403, code: 'csrf' })
  await h.reply(0, csrf('stale-fixture'))
  await h.reply(1, invalidCsrf, 403)
  await rejected
  assert.equal(h.calls.length, 2)
  assert.deepEqual(unauthorized, [])

  const deliberate = session.request('/api/reminders', { method: 'POST', requiresAuth: true })
  assert.equal(h.calls[2].path, '/api/auth/csrf')
  await h.reply(2, csrf('fresh-fixture'))
  assert.equal(h.calls[3].headers.get('X-CSRF-TOKEN'), 'fresh-fixture')
  await h.reply(3, { id: 1 }, 201)
  await deliberate
})

test('ordinary 403, 404 and an ambiguous network failure never replay a write or sign out', async () => {
  const unauthorized = []
  const h = harness((id) => unauthorized.push(id))
  const session = h.client.start()
  const forbidden = session.request('/api/lists/2', { method: 'DELETE', requiresAuth: true })
  const denied = assert.rejects(forbidden, (error) => error instanceof ApiError && error.status === 403 && !error.code)
  await h.reply(0, csrf('fixture-token'))
  await h.reply(1, { detail: 'Access is denied.' }, 403)
  await denied

  const missing = session.request('/api/lists/2', { method: 'DELETE', requiresAuth: true })
  const notFound = assert.rejects(missing, { status: 404 })
  await h.turn()
  assert.equal(h.calls[2].headers.get('X-CSRF-TOKEN'), 'fixture-token')
  await h.reply(2, { detail: 'List was not found.' }, 404)
  await notFound

  const failing = session.request('/api/auth/logout', { method: 'POST', requiresAuth: true })
  const networkError = assert.rejects(failing, /disconnected/)
  await h.turn()
  h.calls[3].reject(new TypeError('disconnected'))
  await networkError
  assert.equal(h.calls.length, 4)
  assert.equal(session.isCurrent(), true)
  assert.deepEqual(unauthorized, [])
})

