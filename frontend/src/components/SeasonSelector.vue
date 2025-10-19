<template>
  <div class="season-selector">
    <label for="season-select" class="season-label">Season:</label>
    <select
      id="season-select"
      v-model="selectedSeason"
      @change="handleSeasonChange"
      class="season-dropdown"
    >
      <option
        v-for="season in seasons"
        :key="season.id"
        :value="season.id"
      >
        {{ season.label }}
      </option>
    </select>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'

const props = defineProps({
  modelValue: {
    type: String,
    default: null
  }
})

const emit = defineEmits(['update:modelValue', 'change'])

const selectedSeason = ref(props.modelValue)

// Generate list of available seasons (current year and 5 years back)
const seasons = computed(() => {
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

  const seasonList = []

  // Generate last 6 seasons (current + 5 previous)
  for (let i = 0; i < 6; i++) {
    const startYear = currentSeasonStart - i
    const endYear = startYear + 1
    const seasonId = `${startYear}${endYear}`
    const seasonLabel = `${startYear}-${endYear}`

    seasonList.push({
      id: seasonId,
      label: seasonLabel,
      startYear: startYear
    })
  }

  return seasonList
})

const handleSeasonChange = () => {
  emit('update:modelValue', selectedSeason.value)
  emit('change', selectedSeason.value)
}

onMounted(() => {
  // Set default to current season if not provided
  if (!selectedSeason.value && seasons.value.length > 0) {
    selectedSeason.value = seasons.value[0].id
    handleSeasonChange()
  }
})
</script>

<style scoped>
.season-selector {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.75rem 1.25rem;
  background-color: var(--color-bg-card);
  border: 2px solid var(--color-border);
  border-radius: 8px;
}

.season-label {
  font-size: 1rem;
  font-weight: bold;
  color: var(--color-text-primary);
  white-space: nowrap;
  text-transform: uppercase;
  letter-spacing: 1px;
}

.season-dropdown {
  padding: 0.5rem 2rem 0.5rem 1rem;
  font-size: 1rem;
  font-weight: bold;
  font-family: var(--font-family);
  color: var(--color-text-primary);
  background-color: var(--color-bg-card);
  border: 2px solid var(--color-border);
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s ease;
  appearance: none;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 12 12'%3E%3Cpath fill='%23ffffff' d='M6 9L1 4h10z'/%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 0.75rem center;
  background-size: 10px;
}

.season-dropdown:hover {
  background-color: rgba(255, 255, 255, 0.05);
  border-color: var(--color-text-secondary);
}

.season-dropdown:focus {
  outline: none;
  border-color: var(--color-text-secondary);
  box-shadow: 0 0 0 2px rgba(255, 170, 0, 0.2);
}

.season-dropdown option {
  background-color: var(--color-bg-card);
  color: var(--color-text-primary);
  padding: 0.5rem;
  font-family: var(--font-family);
}
</style>
