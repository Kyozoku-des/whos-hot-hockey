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
    :class="{ expanded: isExpanded }"
    @click.stop="expandCard"
  >
    <h2 class="card-title">{{ title }}</h2>
    <div class="card-content" :class="{ scrollable: isExpanded }" @click.stop>
      <slot></slot>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'

defineProps({
  title: {
    type: String,
    required: true
  }
})

const isExpanded = ref(false)

const expandCard = () => {
  if (!isExpanded.value) {
    isExpanded.value = true
  }
}

const closeExpanded = () => {
  isExpanded.value = false
}
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
  animation: fadeIn 0.3s ease;
}

@keyframes fadeIn {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

.expandable-card {
  background-color: var(--color-bg-card);
  border: var(--color-border-thick) solid var(--color-border);
  border-radius: 12px;
  padding: 1rem 1.25rem;
  cursor: pointer;
  transition: all 0.3s ease;
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
}

.card-content.scrollable::-webkit-scrollbar-thumb {
  background: var(--color-border);
  border-radius: 6px;
}

.card-content.scrollable::-webkit-scrollbar-thumb:hover {
  background: var(--color-text-secondary);
}
</style>
