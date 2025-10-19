<template>
  <!-- Backdrop -->
  <div
    v-if="isExpanded"
    class="backdrop"
    @click="closeExpanded"
  ></div>

  <!-- Card -->
  <div
    class="expandable-card"
    :class="{ expanded: isExpanded, closing: isClosing }"
    @click.stop="expandCard"
  >
    <!-- Close button (only visible when expanded) -->
    <button
      v-if="isExpanded"
      class="close-button"
      @click.stop="closeExpanded"
      aria-label="Close"
    >
      ✕
    </button>

    <!-- Expand indicator (only visible when not expanded) -->
    <div v-if="!isExpanded" class="expand-indicator">
      ↗
    </div>

    <h2 class="card-title">{{ title }}</h2>
    <div class="card-content" :class="{ scrollable: isExpanded }" @click.stop>
      <slot></slot>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'

defineProps({
  title: {
    type: String,
    required: true
  }
})

const isExpanded = ref(false)
const isClosing = ref(false)

const expandCard = () => {
  if (!isExpanded.value) {
    isExpanded.value = true
    isClosing.value = false
  }
}

const closeExpanded = () => {
  isClosing.value = true
  setTimeout(() => {
    isExpanded.value = false
    isClosing.value = false
  }, 200)
}

const handleEscape = (event) => {
  if (event.key === 'Escape' && isExpanded.value) {
    closeExpanded()
  }
}

onMounted(() => {
  document.addEventListener('keydown', handleEscape)
})

onUnmounted(() => {
  document.removeEventListener('keydown', handleEscape)
})
</script>

<style scoped>
.backdrop {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0, 0, 0, 0.85);
  z-index: 999;
  animation: fadeIn 0.2s ease;
}

@keyframes fadeIn {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

@keyframes fadeOut {
  from {
    opacity: 1;
  }
  to {
    opacity: 0;
  }
}

.expandable-card {
  background-color: var(--color-bg-card);
  border: var(--color-border-thick) solid hsl(0, 0%, 75%);
  border-radius: 12px;
  padding: 1rem 1.25rem;
  cursor: pointer;
  transition: none;
  position: relative;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  height: 100%;
}

.expandable-card:not(.expanded):hover {
  border-color: var(--color-text-secondary);
  box-shadow: 0 0 20px rgba(255, 170, 0, 0.4);
}

.expandable-card.expanded {
  position: fixed;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 90vw;
  max-width: 1400px;
  height: 85vh;
  z-index: 1000;
  box-shadow: 0 0 60px rgba(255, 255, 255, 0.6);
  cursor: default;
  animation: expandCard 0.3s ease;
}

.expandable-card.closing {
  opacity: 0;
  pointer-events: none;
}

.expandable-card.expanded.closing {
  position: fixed;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 90vw;
  max-width: 1400px;
  height: 85vh;
  z-index: 1000;
  animation: fadeOut 0.2s ease forwards;
}

@keyframes expandCard {
  from {
    transform: translate(-50%, -50%) scale(0.8);
    opacity: 0.8;
  }
  to {
    transform: translate(-50%, -50%) scale(1);
    opacity: 1;
  }
}

.card-title {
  font-size: 1.25rem;
  margin-bottom: 0.75rem;
  text-align: center;
  color: var(--color-text-primary);
  text-transform: uppercase;
  letter-spacing: 2px;
  flex-shrink: 0;
}

.card-content {
  flex: 1;
  overflow: hidden;
  color: var(--color-text-secondary);
}

.expandable-card:not(.expanded):hover .card-content {
  overflow-y: auto;
}

.card-content.scrollable {
  overflow-y: auto;
  max-height: calc(85vh - 120px);
}

.card-content.scrollable::-webkit-scrollbar {
  width: 12px;
}

.card-content.scrollable::-webkit-scrollbar-track {
  background: #000;
  border: 2px solid var(--color-border);
  margin-right: 8px;
}

.card-content.scrollable::-webkit-scrollbar-thumb {
  background: var(--color-border);
  border-radius: 6px;
}

.card-content.scrollable::-webkit-scrollbar-thumb:hover {
  background: var(--color-text-secondary);
}

/* Hide scrollbar when not expanded, but keep scrolling functionality */
.expandable-card:not(.expanded) .card-content::-webkit-scrollbar {
  display: none;
}

.expandable-card:not(.expanded) .card-content {
  -ms-overflow-style: none;  /* IE and Edge */
  scrollbar-width: none;  /* Firefox */
}

.close-button {
  position: absolute;
  top: 0.5rem;
  right: 0.5rem;
  width: 24px;
  height: 24px;
  background-color: transparent;
  border: 2px solid var(--color-border);
  color: var(--color-text-primary);
  font-size: 1.1rem;
  line-height: 1;
  cursor: pointer;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
  padding: 0;
  font-family: Arial, sans-serif;
}

.close-button:hover {
  background-color: var(--color-text-secondary);
  border-color: var(--color-text-secondary);
  color: #000;
  transform: scale(1.1);
}

.expand-indicator {
  position: absolute;
  top: 0.75rem;
  right: 0.75rem;
  font-size: 1.5rem;
  color: var(--color-border);
  pointer-events: none;
  opacity: 0.7;
  transition: all 0.2s ease;
  line-height: 1;
}

.expandable-card:not(.expanded):hover .expand-indicator {
  opacity: 1;
  color: var(--color-text-secondary);
  transform: scale(1.2);
}
</style>
