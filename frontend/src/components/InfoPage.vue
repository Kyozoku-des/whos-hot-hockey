<template>
  <div class="info-page">
    <router-link to="/" class="home-link">&lt; Home</router-link>
    <h1 class="info-title">{{ title }}</h1>
    <slot></slot>
  </div>
</template>

<script setup>
// Shared layout for the pages linked below the content (settings and the like)
defineProps({
  title: { type: String, required: true }
})
</script>

<style scoped>
.info-page {
  max-width: 700px;
  margin: 0 auto;
  padding: 2rem 1rem 0;
}

.home-link {
  color: var(--color-text-secondary);
  font-size: 0.9rem;
}

.home-link:hover {
  color: var(--color-text-primary);
}

.info-title {
  font-size: 2rem;
  letter-spacing: 3px;
  text-transform: uppercase;
  text-align: center;
  margin: 1.5rem 0 2rem;
}

.info-page :deep(.info-section) {
  background-color: var(--color-bg-card);
  border: var(--color-border-thick) solid var(--color-border);
  border-radius: 12px;
  padding: 1.25rem;
  margin-bottom: 1.5rem;
}

.info-page :deep(.info-section h2) {
  font-size: 1.1rem;
  margin-bottom: 0.75rem;
  color: var(--color-text-secondary);
}

.info-page :deep(.info-section p) {
  line-height: 1.6;
  font-size: 0.9rem;
}

.info-page :deep(.info-section p + p) {
  margin-top: 0.75rem;
}

.info-page :deep(.info-note) {
  color: rgba(255, 255, 255, 0.6);
  font-size: 0.8rem;
}
</style>
