<template>
  <!-- Clicks stay here instead of expanding the card around it -->
  <span class="position-filter" @click.stop @mousedown.stop @touchstart.stop>
    <select
      class="position-select"
      :value="modelValue"
      :title="selectedTitle"
      aria-label="Filter by position"
      @change="emit('update:modelValue', $event.target.value)"
    >
      <option v-for="option in POSITION_FILTERS" :key="option.value" :value="option.value">
        {{ option.label }}
      </option>
    </select>
  </span>
</template>

<script setup>
import { computed } from 'vue'
import { POSITION_FILTERS } from '../composables/positions'

const props = defineProps({
  modelValue: { type: String, default: 'all' }
})

const emit = defineEmits(['update:modelValue'])

const selectedTitle = computed(() =>
  POSITION_FILTERS.find(option => option.value === props.modelValue)?.title
)
</script>

<style scoped>
.position-filter {
  position: relative;
  display: inline-flex;
  flex-shrink: 0;
}

/* Chevron drawn from two borders, in the text color of the select */
.position-filter::after {
  content: '';
  position: absolute;
  right: 0.6rem;
  top: 50%;
  width: 0.35rem;
  height: 0.35rem;
  border-right: 2px solid currentColor;
  border-bottom: 2px solid currentColor;
  color: var(--color-muted);
  transform: translateY(-70%) rotate(45deg);
  pointer-events: none;
}

.position-select {
  appearance: none;
  padding: 0.2rem 1.6rem 0.2rem 0.6rem;
  font-family: var(--font-family);
  font-size: 0.8rem;
  font-weight: bold;
  letter-spacing: calc(1 * var(--tracking));
  color: var(--color-text-primary);
  background-color: transparent;
  border: var(--color-border-thick) solid var(--color-control-border);
  border-radius: var(--radius-control);
  cursor: pointer;
  transition: border-color 0.2s ease, background-color 0.2s ease;
}

.position-select:hover {
  background-color: rgba(255, 255, 255, 0.05);
  border-color: var(--color-text-secondary);
}

.position-select:focus-visible {
  outline: none;
  border-color: var(--color-text-secondary);
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--color-text-secondary) 20%, transparent);
}

.position-select option {
  background-color: var(--color-bg-card);
  color: var(--color-text-primary);
}
</style>
