<template>
  <p v-if="game" class="stat-line next-game">
    Next game: <span class="opponent">{{ formatOpponent(game) }}</span> · {{ formatGameTime(game) }}
  </p>
</template>

<script setup>
import { computed } from 'vue'
import { useNextGames, formatOpponent, formatGameTime } from '../composables/useNextGames'

const props = defineProps({
  teamCode: { type: String, default: null }
})

const { nextGameFor } = useNextGames()
const game = computed(() => (props.teamCode ? nextGameFor(props.teamCode) : null))
</script>

<style scoped>
.opponent {
  color: var(--color-text-secondary);
  font-weight: bold;
}
</style>
