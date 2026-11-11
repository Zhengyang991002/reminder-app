<script setup>
import { onBeforeUnmount, onMounted, ref, shallowRef } from 'vue'
import App from './App.vue'
import AuthForm from './components/AuthForm.vue'
import { apiErrorMessage, createSessionClient } from './sessionApi.js'

const account = shallowRef(null)
const screen = ref('checking')
const mode = ref('signin')
const authPending = ref(false)
const logoutPending = ref(false)
const formError = ref('')
const notice = ref('')
const logoutError = ref('')
const client = createSessionClient({ onUnauthorized: sessionEnded })
let scope = client.start()

function sessionEnded(id) {
  if (account.value?.session.id === id) {
    showSignIn('Your session ended. Please sign in again.')
  }
}

function resetAccount() {
  scope = client.start()
  account.value = null
  authPending.value = false
  logoutPending.value = false
  logoutError.value = ''
  formError.value = ''
  notice.value = ''
  return scope
}

function showSignIn(message = '') {
  resetAccount()
  mode.value = 'signin'
  notice.value = message
  screen.value = 'form'
}

async function checkSession(expectedUserId) {
  const session = resetAccount()
  screen.value = 'checking'
  try {
    if (expectedUserId !== undefined) {
      await session.getCsrf()
    }
    const user = await session.request('/api/auth/me')
    if (!session.isCurrent()) {
      return
    }
    if (!Number.isSafeInteger(user?.id) || typeof user.email !== 'string'
        || (expectedUserId !== undefined && user.id !== expectedUserId)) {
      throw new Error('Session identity could not be verified.')
    }
    account.value = { user, session }
  } catch (error) {
    if (!session.isCurrent()) {
      return
    }
    if (error?.status === 401) {
      showSignIn(expectedUserId === undefined ? '' : 'Please sign in again to confirm your session.')
    } else {
      screen.value = 'checkFailed'
    }
  }
}

function changeMode(nextMode) {
  if (!authPending.value) {
    mode.value = nextMode
    formError.value = ''
    notice.value = ''
  }
}

async function submitCredentials(credentials) {
  if (authPending.value || screen.value !== 'form') {
    return
  }

  const session = scope
  const registering = mode.value === 'register'
  authPending.value = true
  formError.value = ''
  notice.value = ''

  try {
    const user = await session.request(registering ? '/api/auth/register' : '/api/auth/login', {
      method: 'POST',
      body: credentials
    })
    if (!session.isCurrent()) {
      return
    }

    if (registering) {
      mode.value = 'signin'
      notice.value = 'Account created. Sign in to get started.'
    } else {
      // The login response is only a confirmation. Verify the session before mounting user data.
      await checkSession(user?.id ?? null)
    }
  } catch (error) {
    if (!session.isCurrent()) {
      return
    }
    if (!registering && error?.status === 401) {
      formError.value = 'Invalid email or password.'
    } else if (registering && error?.status === 400
        && error.detail === 'An account with that email already exists.') {
      formError.value = 'An account with that email already exists.'
    } else if (error?.status === 400) {
      formError.value = 'Check your email and password and try again.'
    } else {
      formError.value = apiErrorMessage(error, registering
        ? 'Could not create the account. Check your connection and try again.'
        : 'Could not sign in. Check your connection and try again.')
    }
  } finally {
    if (session.isCurrent()) {
      authPending.value = false
    }
  }
}

async function signOut() {
  const session = account.value?.session
  if (!session || logoutPending.value) {
    return
  }

  logoutPending.value = true
  logoutError.value = ''
  try {
    await session.request('/api/auth/logout', { method: 'POST', requiresAuth: true })
    if (session.isCurrent()) {
      showSignIn('You have signed out.')
    }
  } catch (error) {
    if (session.isCurrent()) {
      logoutError.value = apiErrorMessage(error, 'Could not confirm sign-out. Try again or check your session.')
    }
  } finally {
    if (session.isCurrent()) {
      logoutPending.value = false
    }
  }
}

onMounted(() => { void checkSession() })
onBeforeUnmount(() => { client.start() })
</script>

<template>
  <App v-if="account" :key="account.session.id" :session="account.session">
    <template #account>
      <div class="account-bar" aria-label="Signed-in account">
        <span class="account-email">{{ account.user.email }}</span>
        <button class="cancel-button" type="button" :disabled="logoutPending" @click="signOut">
          {{ logoutPending ? 'Signing out…' : 'Sign out' }}
        </button>
      </div>
      <div v-if="logoutError" class="account-error">
        <p class="error" role="alert">{{ logoutError }}</p>
        <button class="cancel-button" type="button" @click="checkSession()">Check session</button>
      </div>
    </template>
  </App>

  <AuthForm
    v-else-if="screen === 'form'"
    :mode="mode"
    :busy="authPending"
    :error="formError"
    :notice="notice"
    @update:mode="changeMode"
    @submit="submitCredentials"
  />

  <main v-else class="auth-shell">
    <section class="auth-card" aria-labelledby="session-heading">
      <h1 id="session-heading">Reminders</h1>
      <p v-if="screen === 'checking'" role="status">Checking your session…</p>
      <template v-else>
        <p class="error" role="alert">Could not verify your session. Check your connection and try again.</p>
        <button class="auth-primary" type="button" @click="checkSession()">Check session</button>
      </template>
    </section>
  </main>
</template>

