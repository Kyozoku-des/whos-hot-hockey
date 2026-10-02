<template>
  <button
    type="button"
    class="favorite-button"
    :class="{ favorited: isFav }"
    :disabled="!isFav && !canAddMore()"
    @click="toggleFavorite(item)"
  >
    <PixelIcon name="star" :filled="isFav" class="favorite-icon" />
    {{ label }}
  </button>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import PixelIcon from './PixelIcon.vue'
import { useFavorites } from '../composables/useFavorites'

const props = defineProps({
  item: {
    type: Object,
    required: true
  }
})

const { initializeFavorites, isFavorited, toggleFavorite, canAddMore } = useFavorites()

const isFav = computed(() => isFavorited(props.item.id))
const label = computed(() => {
  if (isFav.value) return 'Remove from favorites'
  return canAddMore() ? 'Add to favorites' : 'Favorites full (max 10)'
})

// The page may be opened directly, before the home page has loaded favorites
onMounted(initializeFavorites)
</script>

<style scoped>
.favorite-button {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  margin-top: 0.75rem;
  padding: 0.5rem 1rem;
  font-family: var(--font-family);
  font-size: 0.9rem;
  color: var(--color-text-primary);
  background: transparent;
  border: 2px solid var(--color-border);
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.favorite-button:hover:not(:disabled) {
  border-color: var(--color-text-secondary);
}

.favorite-button.favorited {
  border-color: var(--color-text-secondary);
}

.favorite-button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.favorite-icon {
  color: #FFD700;
}
</style>
