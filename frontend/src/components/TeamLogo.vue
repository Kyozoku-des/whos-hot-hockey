<template>
  <img
    :src="imageSrc"
    :alt="alt"
    :class="['team-logo', sizeClass]"
    @error="handleImageError"
  />
</template>

<script setup>
import { ref, computed } from 'vue'

const props = defineProps({
  logoUrl: {
    type: String,
    default: null
  },
  teamCode: {
    type: String,
    default: ''
  },
  alt: {
    type: String,
    default: 'Team Logo'
  },
  size: {
    type: String,
    default: 'medium', // small, medium, large
    validator: (value) => ['small', 'medium', 'large'].includes(value)
  }
})

const imageError = ref(false)

// Generate placeholder SVG with team code
const generatePlaceholder = () => {
  const svg = `
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">
      <rect width="100" height="100" fill="#1a1a1a"/>
      <text x="50" y="50" font-family="Arial, sans-serif" font-size="24" fill="#666" text-anchor="middle" dominant-baseline="middle">
        ${props.teamCode || '?'}
      </text>
    </svg>
  `
  return `data:image/svg+xml;base64,${btoa(svg)}`
}

const imageSrc = computed(() => {
  if (imageError.value || !props.logoUrl) {
    return generatePlaceholder()
  }
  return props.logoUrl
})

const sizeClass = computed(() => `logo-${props.size}`)

const handleImageError = () => {
  imageError.value = true
}
</script>

<style scoped>
.team-logo {
  object-fit: contain;
  background-color: transparent;
}

.logo-small {
  width: 32px;
  height: 32px;
}

.logo-medium {
  width: 48px;
  height: 48px;
}

.logo-large {
  width: 120px;
  height: 120px;
}
</style>
