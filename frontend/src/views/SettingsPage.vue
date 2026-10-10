<template>
  <InfoPage title="Settings">
    <section class="info-section">
      <h2>Card order on mobile</h2>
      <p class="info-note">Order of the swipeable cards on the home page on phones.</p>
      <ol class="card-order">
        <li v-for="(key, index) in mobileCardOrder" :key="key" class="card-order-item">
          <span class="card-order-name">{{ labelFor(key) }}</span>
          <span class="card-order-buttons">
            <button
              class="move-button"
              :disabled="index === 0"
              :aria-label="`Move ${labelFor(key)} up`"
              @click="move(index, -1)"
            >&#9650;</button>
            <button
              class="move-button"
              :disabled="index === mobileCardOrder.length - 1"
              :aria-label="`Move ${labelFor(key)} down`"
              @click="move(index, 1)"
            >&#9660;</button>
          </span>
        </li>
      </ol>
      <button class="reset-button" @click="resetMobileCardOrder">Reset order</button>
    </section>

    <section class="info-section">
      <h2>Time zone</h2>
      <p class="info-note">Used for game start times and the last updated time.</p>
      <select v-model="timeZone" class="setting-select" aria-label="Time zone">
        <option v-for="zone in TIME_ZONES" :key="zone.value" :value="zone.value">{{ zone.label }}</option>
      </select>
    </section>

    <section class="info-section">
      <h2>Theme</h2>
      <p class="info-note">More themes coming later.</p>
      <select v-model="theme" class="setting-select" aria-label="Theme" disabled>
        <option v-for="option in THEMES" :key="option.value" :value="option.value">{{ option.label }}</option>
      </select>
    </section>

    <p class="info-note storage-note">
      <template v-if="consentGiven === true">Settings are saved in this browser.</template>
      <template v-else>
        Settings last for this visit only. Enable favorites storage on the home page to remember them.
      </template>
    </p>
  </InfoPage>
</template>

<script setup>
import { onMounted } from 'vue'
import InfoPage from '../components/InfoPage.vue'
import { useFavorites } from '../composables/useFavorites'
import { useSettings, HOME_CARDS, TIME_ZONES, THEMES } from '../composables/useSettings'

const { consentGiven, checkConsent } = useFavorites()
const { mobileCardOrder, timeZone, theme, resetMobileCardOrder } = useSettings()

const labelFor = (key) => HOME_CARDS.find(card => card.key === key)?.label ?? key

const move = (index, step) => {
  const order = [...mobileCardOrder.value]
  ;[order[index], order[index + step]] = [order[index + step], order[index]]
  mobileCardOrder.value = order
}

onMounted(checkConsent)
</script>

<style scoped>
.card-order {
  list-style: none;
  margin: 1rem 0;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.card-order-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  padding: 0.5rem 0.75rem;
  border: 1px solid rgba(255, 255, 255, 0.3);
  border-radius: 8px;
  font-size: 0.9rem;
}

.card-order-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-order-buttons {
  display: flex;
  gap: 0.5rem;
  flex-shrink: 0;
}

.move-button,
.reset-button,
.setting-select {
  font-family: var(--font-family);
  color: var(--color-text-primary);
  background-color: var(--color-bg-primary);
  border: var(--color-border-thick) solid var(--color-border);
  border-radius: 6px;
}

.move-button {
  width: 2.25rem;
  height: 2.25rem;
  font-size: 0.8rem;
}

.reset-button {
  padding: 0.5rem 1rem;
  font-size: 0.85rem;
  letter-spacing: 1px;
}

.move-button:hover:not(:disabled),
.reset-button:hover {
  color: var(--color-text-secondary);
  border-color: var(--color-text-secondary);
}

.move-button:disabled,
.setting-select:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}

.setting-select {
  margin-top: 1rem;
  width: 100%;
  padding: 0.6rem 0.75rem;
  font-size: 0.9rem;
  letter-spacing: 1px;
}

.storage-note {
  text-align: center;
}
</style>
