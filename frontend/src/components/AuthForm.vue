<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { normalizeNotes } from '../notes.js'

const props = defineProps({
  mode: { type: String, required: true },
  busy: Boolean,
  error: { type: String, default: '' },
  notice: { type: String, default: '' }
})
const emit = defineEmits(['update:mode', 'submit'])

const email = ref('')
const password = ref('')
const confirmation = ref('')
const validationError = ref('')
const invalidField = ref('')
const emailInput = ref(null)
const passwordInput = ref(null)
const confirmationInput = ref(null)
const registering = computed(() => props.mode === 'register')
const errorMessage = computed(() => validationError.value || props.error)

const emailPattern = /^[^\s@]+@(?:[\p{L}\p{N}](?:[\p{L}\p{N}-]*[\p{L}\p{N}])?\.)*[\p{L}\p{N}](?:[\p{L}\p{N}-]*[\p{L}\p{N}])?$/u

function clearPasswords() {
  password.value = ''
  confirmation.value = ''
}

function clearValidation() {
  validationError.value = ''
  invalidField.value = ''
}

function focusEmail() {
  emailInput.value?.focus()
}

onMounted(focusEmail)
onBeforeUnmount(clearPasswords)
watch(() => props.mode, async () => {
  clearPasswords()
  clearValidation()
  await nextTick()
  focusEmail()
})

function markInvalid(field, input, message) {
  invalidField.value = field
  validationError.value = message
  input?.focus()
}

function submit() {
  if (props.busy) return

  clearValidation()
  const normalizedEmail = normalizeNotes(email.value)?.toLowerCase() ?? ''
  if (normalizedEmail.length > 254 || !emailPattern.test(normalizedEmail)) {
    markInvalid('email', emailInput.value, 'Enter a valid email address.')
    return
  }

  // Use Java-compatible blank checking without changing the submitted password.
  const enteredPassword = password.value
  if (!normalizeNotes(enteredPassword)) {
    markInvalid('password', passwordInput.value, 'Enter your password.')
    return
  }
  if (enteredPassword.includes('\0') || /[\uD800-\uDFFF]/u.test(enteredPassword)) {
    markInvalid('password', passwordInput.value, 'Enter a password with valid text.')
    return
  }
  if (enteredPassword.length > 72 || new TextEncoder().encode(enteredPassword).length > 72) {
    markInvalid('password', passwordInput.value, 'Your password must be at most 72 UTF-8 bytes.')
    return
  }
  if (registering.value && [...enteredPassword].length < 12) {
    markInvalid('password', passwordInput.value, 'Use at least 12 characters for your password.')
    return
  }
  if (registering.value && enteredPassword !== confirmation.value) {
    markInvalid('confirmation', confirmationInput.value, 'The passwords do not match.')
    return
  }

  email.value = normalizedEmail
  emit('submit', { email: normalizedEmail, password: enteredPassword })
}
</script>

<template>
  <main class="auth-shell">
    <section class="auth-card" aria-labelledby="auth-title">
      <h1 id="auth-title">{{ registering ? 'Create account' : 'Sign in to Reminders' }}</h1>
      <p v-if="notice" class="auth-notice" role="status">{{ notice }}</p>
      <form class="auth-form" method="post" novalidate :aria-busy="busy" @submit.prevent="submit">
        <label for="auth-email">Email</label>
        <input
          id="auth-email"
          ref="emailInput"
          v-model="email"
          name="email"
          type="email"
          autocomplete="username"
          autocapitalize="none"
          :spellcheck="false"
          :aria-invalid="invalidField === 'email'"
          :aria-describedby="invalidField === 'email' ? 'auth-error' : undefined"
          :disabled="busy"
          required
          @input="clearValidation"
        >

        <label for="auth-password">Password</label>
        <input
          id="auth-password"
          ref="passwordInput"
          v-model="password"
          name="password"
          type="password"
          :autocomplete="registering ? 'new-password' : 'current-password'"
          :aria-invalid="invalidField === 'password'"
          :aria-describedby="[registering && 'auth-password-help', invalidField === 'password' && 'auth-error'].filter(Boolean).join(' ') || undefined"
          :disabled="busy"
          required
          @input="clearValidation"
        >
        <small v-if="registering" id="auth-password-help" class="auth-help">
          At least 12 characters and at most 72 UTF-8 bytes.
        </small>

        <template v-if="registering">
          <label for="auth-confirmation">Confirm password</label>
          <input
            id="auth-confirmation"
            ref="confirmationInput"
            v-model="confirmation"
            name="password-confirmation"
            type="password"
            autocomplete="new-password"
            :aria-invalid="invalidField === 'confirmation'"
            :aria-describedby="invalidField === 'confirmation' ? 'auth-error' : undefined"
            :disabled="busy"
            required
            @input="clearValidation"
          >
        </template>

        <p v-if="errorMessage" id="auth-error" class="error" role="alert">{{ errorMessage }}</p>
        <button class="auth-primary" type="submit" :disabled="busy">
          {{ registering ? (busy ? 'Creating account…' : 'Create account') : (busy ? 'Signing in…' : 'Sign in') }}
        </button>
      </form>

      <p class="auth-mode">
        {{ registering ? 'Already have an account?' : 'New to Reminders?' }}
        <button
          type="button"
          class="auth-mode-button"
          :disabled="busy"
          @click="emit('update:mode', registering ? 'signin' : 'register')"
        >
          {{ registering ? 'Sign in' : 'Create account' }}
        </button>
      </p>
    </section>
  </main>
</template>
