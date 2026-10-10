<template>
  <InfoPage title="Feedback">
    <section class="info-section">
      <h2>Tell us what you think</h2>
      <p>Found a bug, missing a stat or have an idea? Write it below and send it the way that suits you.</p>
      <textarea
        v-model="message"
        class="feedback-input"
        rows="6"
        maxlength="2000"
        placeholder="Your feedback..."
        aria-label="Feedback"
      ></textarea>
      <div class="feedback-actions">
        <a class="feedback-button" :href="issueUrl" target="_blank" rel="noopener">Open GitHub issue</a>
        <a v-if="FEEDBACK_EMAIL" class="feedback-button" :href="mailUrl">Send email</a>
      </div>
      <p class="info-note">
        GitHub issues are public and need a GitHub account.
        <template v-if="FEEDBACK_EMAIL">Email goes straight to the developer.</template>
      </p>
    </section>
  </InfoPage>
</template>

<script setup>
import { computed, ref } from 'vue'
import InfoPage from '../components/InfoPage.vue'

const REPO_URL = 'https://github.com/Kyozoku-des/whos-hot-nhl'
// Set at build time; the email option is hidden when it is not set
const FEEDBACK_EMAIL = import.meta.env.VITE_FEEDBACK_EMAIL || ''
const SUBJECT = 'Feedback: Whos Hot NHL'

const message = ref('')

// The first line doubles as the issue title
const issueUrl = computed(() => {
  const firstLine = message.value.trim().split('\n')[0].slice(0, 80)
  const params = new URLSearchParams({
    title: firstLine ? `Feedback: ${firstLine}` : 'Feedback',
    body: message.value.trim()
  })
  return `${REPO_URL}/issues/new?${params}`
})

const mailUrl = computed(() =>
  `mailto:${FEEDBACK_EMAIL}?subject=${encodeURIComponent(SUBJECT)}&body=${encodeURIComponent(message.value.trim())}`
)
</script>

<style scoped>
.feedback-input {
  display: block;
  width: 100%;
  margin-top: 1rem;
  padding: 0.75rem;
  font-family: var(--font-family);
  font-size: 0.9rem;
  letter-spacing: 1px;
  color: var(--color-text-primary);
  background-color: var(--color-bg-primary);
  border: var(--color-border-thick) solid var(--color-border);
  border-radius: 8px;
  resize: vertical;
}

.feedback-input:focus {
  outline: none;
  border-color: var(--color-text-secondary);
}

.feedback-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
  margin: 1rem 0;
}

.feedback-button {
  padding: 0.6rem 1.2rem;
  font-size: 0.9rem;
  color: #000;
  background-color: #FFAA00;
  border-radius: 8px;
  transition: background-color 0.2s ease;
}

.feedback-button:hover {
  background-color: #FFB820;
}
</style>
