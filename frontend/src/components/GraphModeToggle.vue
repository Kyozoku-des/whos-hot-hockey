<template>
  <div class="graph-mode-toggle" role="group" aria-label="Graph mode">
    <button
      v-for="option in options"
      :key="option.value"
      type="button"
      :class="['mode-btn', { active: graphMode === option.value }]"
      :aria-pressed="graphMode === option.value"
      @click="graphMode = option.value"
    >
      {{ option.label }}
    </button>
  </div>
</template>

<script setup>
import { useGraphMode } from '../composables/useGraphMode'

const { graphMode } = useGraphMode()

const options = [
  { value: 'cumulative', label: 'Season total' },
  { value: 'perGame', label: 'Per game' }
]
</script>

<style scoped>
.graph-mode-toggle {
  display: flex;
  justify-content: center;
  gap: 0.5rem;
  margin-bottom: 1rem;
}

.mode-btn {
  padding: 0.4rem 0.9rem;
  font-family: var(--font-family);
  font-size: 0.9rem;
  color: var(--color-text-primary);
  background: transparent;
  border: 2px solid var(--color-border);
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.mode-btn:hover {
  border-color: var(--color-text-secondary);
}

.mode-btn.active {
  background-color: var(--color-text-secondary);
  border-color: var(--color-text-secondary);
  color: #000;
}
</style>
