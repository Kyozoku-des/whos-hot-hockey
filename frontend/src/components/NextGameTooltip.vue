<template>
  <Teleport to="body">
    <div v-if="hovered" class="next-game-tooltip" :style="position" role="tooltip">
      <span class="label">Next:</span>
      <span class="opponent">{{ formatOpponent(hovered.game) }}</span>
      <span class="time">{{ formatGameTime(hovered.game) }}</span>
    </div>
  </Teleport>
</template>

<script setup>
import { computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import { useNextGames, formatOpponent, formatGameTime } from '../composables/useNextGames'

const route = useRoute()
const { hovered, hideNextGame } = useNextGames()

// Clicking a row navigates away before its mouseleave fires
watch(() => route.fullPath, hideNextGame)

// Above the hovered row's right edge; below it when the row is near the top of the viewport.
// Fixed and teleported so the cards' scroll containers cannot clip it.
const position = computed(() => {
  const { rect } = hovered.value
  const above = rect.top > 48
  return {
    top: `${above ? rect.top - 6 : rect.bottom + 6}px`,
    left: `${rect.right - 12}px`,
    transform: `translate(-100%, ${above ? '-100%' : '0'})`
  }
})

// The tooltip is placed from the row's position when hovered, so it would drift on scroll
onMounted(() => {
  window.addEventListener('scroll', hideNextGame, true)
})

onBeforeUnmount(() => {
  window.removeEventListener('scroll', hideNextGame, true)
})
</script>

<style scoped>
.next-game-tooltip {
  position: fixed;
  z-index: 200;
  display: flex;
  gap: 0.4rem;
  padding: 0.4rem 0.75rem;
  background-color: var(--color-bg-card);
  border: var(--color-border-thick) solid var(--color-text-secondary);
  border-radius: 6px;
  font-size: 0.85rem;
  white-space: nowrap;
  pointer-events: none;
}

.label {
  color: var(--color-text-primary);
  opacity: 0.8;
}

.opponent {
  color: var(--color-text-secondary);
  font-weight: bold;
}

.time {
  color: var(--color-text-primary);
}
</style>
