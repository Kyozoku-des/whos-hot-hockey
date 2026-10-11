<template>
  <InfoPage title="Feedback">
    <section class="info-section">
      <h2>Tell us what you think</h2>
      <p>Found a bug, missing a stat or have an idea? Send it straight to the developer.</p>
      <div class="quick-links">
        <router-link :to="{ query: { type: 'bug' } }" replace class="quick-link">Report a bug</router-link>
        <router-link :to="{ query: { type: 'feature' } }" replace class="quick-link">Suggest a feature</router-link>
      </div>

      <form v-if="status !== 'sent'" class="feedback-form" novalidate @submit.prevent="submit">
        <label class="field">
          <span class="field-label">Type</span>
          <select v-model="type" class="field-input">
            <option v-for="option in TYPES" :key="option.value" :value="option.value">{{ option.label }}</option>
          </select>
        </label>

        <label class="field">
          <span class="field-label">Message *</span>
          <textarea
            v-model="message"
            class="field-input"
            :class="{ invalid: errors.message }"
            rows="6"
            :maxlength="MESSAGE_MAX"
            placeholder="What happened, or what would you like to see?"
          ></textarea>
          <span class="field-hint">
            <span v-if="errors.message" class="field-error">{{ errors.message }}</span>
            <span class="char-count">{{ message.length }}/{{ MESSAGE_MAX }}</span>
          </span>
        </label>

        <label class="field">
          <span class="field-label">Email (optional)</span>
          <input
            v-model="email"
            type="email"
            class="field-input"
            :class="{ invalid: errors.email }"
            maxlength="200"
            placeholder="Only if you'd like a reply"
            autocomplete="email"
          />
          <span v-if="errors.email" class="field-hint field-error">{{ errors.email }}</span>
        </label>

        <!-- Honeypot: hidden from people, filled in by bots; Web3Forms drops
             submissions where it is set -->
        <input v-model="botcheck" type="checkbox" name="botcheck" class="honeypot" tabindex="-1" autocomplete="off" />

        <p v-if="status === 'error'" class="form-message form-error" role="alert">{{ errorMessage }}</p>
        <p v-if="!ACCESS_KEY" class="info-note">The feedback form isn't set up yet. Check back soon!</p>

        <button type="submit" class="submit-button" :disabled="status === 'sending' || !ACCESS_KEY">
          {{ status === 'sending' ? 'Sending...' : 'Send feedback' }}
        </button>
        <p class="info-note">The page you came from and the app version are sent along to help track down issues.</p>
      </form>

      <div v-else class="form-message form-success" role="status">
        <p>Thanks! Your feedback was sent.</p>
        <button type="button" class="submit-button" @click="reset">Send more</button>
      </div>
    </section>
  </InfoPage>
</template>

<script setup>
import { reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import InfoPage from '../components/InfoPage.vue'

// Web3Forms access key, set at build time. It is a public form ID, not a
// secret: Web3Forms only forwards submissions to the inbox registered with it.
const ACCESS_KEY = import.meta.env.VITE_WEB3FORMS_ACCESS_KEY || ''
const ENDPOINT = 'https://api.web3forms.com/submit'

const TYPES = [
  { value: 'bug', label: 'Bug report' },
  { value: 'feature', label: 'Feature suggestion' },
  { value: 'other', label: 'Other' }
]
const MESSAGE_MIN = 10
const MESSAGE_MAX = 2000
// Bots fill and send forms instantly; people take longer than this
const MIN_FILL_MS = 3000
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

const route = useRoute()

const typeFromQuery = () => (TYPES.some(option => option.value === route.query.type) ? route.query.type : 'other')

const type = ref(typeFromQuery())
const message = ref('')
const email = ref('')
const botcheck = ref(false)
const errors = reactive({ message: '', email: '' })
// idle | sending | sent | error
const status = ref('idle')
const errorMessage = ref('')
let openedAt = Date.now()

// The bug / feature links only change the query, so follow it
watch(() => route.query.type, () => { type.value = typeFromQuery() })

// Vue Router keeps the previous path in history.state, so the report points at
// the page the user was on rather than this form
const sourcePageUrl = () => {
  const back = window.history.state?.back
  return back ? new URL(back, window.location.origin).href : window.location.href
}

const validate = () => {
  const text = message.value.trim()
  errors.message = !text
    ? 'Please write a message.'
    : text.length < MESSAGE_MIN ? `Please write at least ${MESSAGE_MIN} characters.` : ''
  const address = email.value.trim()
  errors.email = address && !EMAIL_PATTERN.test(address) ? 'That email address doesn\'t look right.' : ''
  return !errors.message && !errors.email
}

const fail = (text) => {
  status.value = 'error'
  errorMessage.value = text
}

const submit = async () => {
  if (status.value === 'sending' || !validate()) return
  if (Date.now() - openedAt < MIN_FILL_MS) {
    fail('That was quick! Please wait a moment and try again.')
    return
  }

  const typeLabel = TYPES.find(option => option.value === type.value).label
  const payload = {
    access_key: ACCESS_KEY,
    subject: `Whos Hot NHL: ${typeLabel}`,
    from_name: 'Whos Hot NHL feedback',
    type: typeLabel,
    message: message.value.trim(),
    page_url: sourcePageUrl(),
    app_version: __APP_VERSION__,
    botcheck: botcheck.value
  }
  // Web3Forms uses "email" as the reply-to address, so only send it when given
  if (email.value.trim()) payload.email = email.value.trim()

  status.value = 'sending'
  try {
    const response = await fetch(ENDPOINT, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify(payload)
    })
    const result = await response.json().catch(() => ({}))
    if (response.ok && result.success) {
      status.value = 'sent'
    } else if (response.status === 429) {
      fail('Too many messages right now. Please try again in a little while.')
    } else {
      fail('Sorry, your feedback could not be sent. Please try again later.')
    }
  } catch {
    fail('Could not reach the feedback service. Check your connection and try again.')
  }
}

const reset = () => {
  message.value = ''
  email.value = ''
  errors.message = ''
  errors.email = ''
  status.value = 'idle'
  openedAt = Date.now()
}
</script>

<style scoped>
.quick-links {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem 1.5rem;
  margin-top: 0.75rem;
  font-size: 0.85rem;
}

.quick-link {
  color: var(--color-text-secondary);
  text-decoration: underline;
  text-underline-offset: 3px;
}

.quick-link:hover {
  color: var(--color-text-primary);
}

.feedback-form {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  margin-top: 1.25rem;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}

.field-label {
  font-size: 0.85rem;
}

.field-input {
  width: 100%;
  padding: 0.6rem 0.75rem;
  font-family: var(--font-family);
  font-size: 0.9rem;
  letter-spacing: 1px;
  color: var(--color-text-primary);
  background-color: var(--color-bg-primary);
  border: var(--color-border-thick) solid var(--color-border);
  border-radius: 8px;
}

textarea.field-input {
  resize: vertical;
}

.field-input:focus {
  outline: none;
  border-color: var(--color-text-secondary);
}

.field-input.invalid {
  border-color: hsl(0, 85%, 62%);
}

.field-hint {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  font-size: 0.75rem;
}

.char-count {
  margin-left: auto;
  color: rgba(255, 255, 255, 0.5);
}

.field-error,
.form-error {
  color: hsl(0, 85%, 62%);
}

.honeypot {
  display: none;
}

.form-message {
  font-size: 0.9rem;
}

.form-success {
  margin-top: 1.25rem;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 1rem;
  color: #7dff7d;
}

.submit-button {
  align-self: flex-start;
  padding: 0.6rem 1.2rem;
  font-family: var(--font-family);
  font-size: 0.9rem;
  color: #000;
  background-color: #FFAA00;
  border: none;
  border-radius: 8px;
  transition: background-color 0.2s ease;
}

.submit-button:hover:not(:disabled) {
  background-color: #FFB820;
}

.submit-button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
