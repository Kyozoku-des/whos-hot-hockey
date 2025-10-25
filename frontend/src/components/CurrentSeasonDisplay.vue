<template>
  <div class="current-season-display">
    <span class="season-label">Season</span>
    <span class="season-value">{{ currentSeason }}</span>
  </div>
</template>

<script setup>
import { computed } from 'vue'

// Calculate current NHL season
const currentSeason = computed(() => {
  const currentYear = new Date().getFullYear()
  const currentMonth = new Date().getMonth() + 1 // 1-12

  // Determine current NHL season
  let currentSeasonStart
  if (currentMonth >= 1 && currentMonth <= 6) {
    // Jan-June: Still in previous season
    currentSeasonStart = currentYear - 1
  } else if (currentMonth >= 10) {
    // Oct-Dec: New season has started
    currentSeasonStart = currentYear
  } else {
    // July-Sept: Prepare for next season
    currentSeasonStart = currentYear
  }

  const endYear = currentSeasonStart + 1
  return `${String(currentSeasonStart).slice(-2)}/${String(endYear).slice(-2)}`
})
</script>

<style scoped>
.current-season-display {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.2rem;
  padding: 1rem 0rem;
  background-color: var(--color-bg-card);
}

.season-label {
  font-size: 0.9rem;
  font-weight: bold;
  color: var(--color-text-primary);
  white-space: nowrap;
  text-transform: uppercase;
  letter-spacing: 1px;
}

.season-value {
  padding: 0.4rem 0.75rem;
  font-size: 0.85rem;
  font-weight: bold;
  font-family: var(--font-family);
  color: var(--color-text-primary);
  background-color: transparent;
  border: 2px solid var(--color-border);
  border-radius: 6px;
  text-align: center;
}
</style>
