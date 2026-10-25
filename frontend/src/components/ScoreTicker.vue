<template>
  <TickerLine
    class="score-ticker"
    :items="games"
    :item-key="game => game.gameId"
    :label="live ? 'LIVE' : dayLabel"
    :aria-label="ariaLabel"
    :speed="SPEED"
  >
    <template #default="{ item: game }">
      <span class="game-score">
        {{ game.awayTeamCode }} {{ score(game.awayScore) }} - {{ score(game.homeScore) }} {{ game.homeTeamCode }}
        <span class="game-status">{{ status(game) }}</span>
      </span>
      <span v-for="goal in game.goals" :key="goal.goalNumber" class="ticker-goal">
        <span class="goal-team">{{ goal.teamCode }}</span>
        <span :class="pointClass(goal.scorer)" :title="pointTitle(goal.scorer)">{{ goal.scorer.name }}</span>
        <template v-if="goal.assists.length">
          (<template v-for="(assist, index) in goal.assists" :key="assist.playerId"><template v-if="index">, </template><span :class="pointClass(assist)" :title="pointTitle(assist)">{{ assist.name }}</span></template>)
        </template>
      </span>
    </template>
  </TickerLine>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useGameScores } from '../composables/useApi'
import TickerLine from './TickerLine.vue'

// Refresh every minute while games are live, otherwise every ten minutes
const LIVE_REFRESH_MS = 60 * 1000
const IDLE_REFRESH_MS = 10 * 60 * 1000
// Scroll speed in pixels per second
const SPEED = 60

const { getLatestScoreboard } = useGameScores()

const games = ref([])
const live = ref(false)
const gameDate = ref(null)

let refreshTimer = null
// Set on unmount so a request still in flight cannot start a new refresh loop
let disposed = false

const dayLabel = computed(() => {
  if (!gameDate.value) return ''
  const [year, month, day] = gameDate.value.split('-').map(Number)
  return new Date(year, month - 1, day)
    .toLocaleDateString('en-US', { month: 'short', day: 'numeric' })
    .toUpperCase()
})

const ariaLabel = computed(() => (live.value ? 'Live scores' : `Scores from ${dayLabel.value}`))

const score = (value) => (value ?? 0)

const status = (game) => {
  if (game.gameState === 'LIVE' || game.gameState === 'CRIT') return 'LIVE'
  if (game.lastPeriodType === 'OT' || game.lastPeriodType === 'SO') return `FINAL/${game.lastPeriodType}`
  return 'FINAL'
}

// Players who extended a point streak or are hot over the last ten games are shown in red
const pointClass = (point) => ['ticker-player', { highlighted: point.streakExtended || point.hot }]

const pointTitle = (point) => {
  const reasons = []
  if (point.streakExtended) reasons.push('Extended point streak')
  if (point.hot) reasons.push('Hot over last 10 games')
  return reasons.length ? reasons.join(' · ') : undefined
}

const load = async () => {
  const scoreboard = await getLatestScoreboard()
  if (disposed) return
  games.value = scoreboard.games
  live.value = scoreboard.live
  gameDate.value = scoreboard.gameDate
  scheduleRefresh()
}

const scheduleRefresh = () => {
  clearTimeout(refreshTimer)
  if (disposed) return
  refreshTimer = setTimeout(load, live.value ? LIVE_REFRESH_MS : IDLE_REFRESH_MS)
}

onMounted(load)

onUnmounted(() => {
  disposed = true
  clearTimeout(refreshTimer)
})
</script>

<style scoped>
.score-ticker {
  --color-ticker-highlight: var(--color-negative, hsl(0, 85%, 62%));
}

.game-score {
  color: var(--color-text-secondary);
}

.game-status {
  margin-left: 0.5rem;
  font-size: 0.75em;
}

.ticker-goal {
  margin-left: 1.25rem;
}

.goal-team {
  margin-right: 0.4rem;
  opacity: 0.6;
}

.ticker-player.highlighted {
  color: var(--color-ticker-highlight);
}
</style>
