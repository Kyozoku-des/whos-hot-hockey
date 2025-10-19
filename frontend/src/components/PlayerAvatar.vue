<template>
  <img
    :src="imageSrc"
    :alt="alt"
    :class="['player-avatar', sizeClass]"
    @error="handleImageError"
  />
</template>

<script setup>
import { ref, computed } from 'vue'

const props = defineProps({
  headshotUrl: {
    type: String,
    default: null
  },
  alt: {
    type: String,
    default: 'Player'
  },
  size: {
    type: String,
    default: 'medium', // small, medium, large
    validator: (value) => ['small', 'medium', 'large'].includes(value)
  }
})

const imageError = ref(false)
const placeholderUrl = new URL('../assets/player-placeholder.svg', import.meta.url).href

const imageSrc = computed(() => {
  if (imageError.value || !props.headshotUrl) {
    return placeholderUrl
  }
  return props.headshotUrl
})

const sizeClass = computed(() => `avatar-${props.size}`)

const handleImageError = () => {
  imageError.value = true
}
</script>

<style scoped>
.player-avatar {
  border-radius: 4px;
  object-fit: cover;
  background-color: var(--color-bg-dark);
}

.avatar-small {
  width: 32px;
  height: 32px;
}

.avatar-medium {
  width: 48px;
  height: 48px;
}

.avatar-large {
  width: 200px;
  height: 200px;
}
</style>
