<template>
  <div
    v-if="games.length"
    class="score-ticker"
    :class="{ 'reduced-motion': reducedMotion }"
    :aria-label="ariaLabel"
    role="marquee"
  >
    <div class="ticker-label">{{ live ? 'LIVE' : dayLabel }}</div>
    <div class="ticker-viewport" :ref="setViewport">
      <!-- Two equal halves, each wide enough to fill the line, so the loop scrolls seamlessly;
           only the first copy is read by screen readers -->
      <div class="ticker-track" :style="{ animationDuration: `${duration}s` }">
        <div
          v-for="copy in copies"
          :key="copy"
          class="ticker-content"
          :ref="el => copy === 0 && setContent(el)"
          :aria-hidden="copy > 0 ? 'true' : undefined"
        >
          <span v-for="game in games" :key="game.gameId" class="ticker-game">
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
          </span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { useGameScores } from '../composables/useApi'

// Refresh every minute while games are live, otherwise every ten minutes
const LIVE_REFRESH_MS = 60 * 1000
const IDLE_REFRESH_MS = 10 * 60 * 1000
// Scroll speed in pixels per second
const SPEED = 60

const { getLatestScoreboard } = useGameScores()

const games = ref([])
const live = ref(false)
const gameDate = ref(null)
const duration = ref(60)
// Copies of the content in each half of the track
const repeats = ref(1)
const reducedMotion = ref(false)

// Measured to fill the line and set the scroll duration
let content = null
let viewport = null
const setContent = (el) => {
  content = el
}
const setViewport = (el) => {
  viewport = el
}
let refreshTimer = null
let motionQuery = null
// Set on unmount so a request still in flight cannot start a new refresh loop
let disposed = false

// With reduced motion the content is shown once and scrolled by hand
const copies = computed(() =>
  reducedMotion.value ? [0] : Array.from({ length: repeats.value * 2 }, (_, index) => index)
)

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

// Repeat short content so no gap shows, and keep the scroll speed constant however much there is
const updateLayout = () => {
  const width = content?.offsetWidth
  if (!width) return
  repeats.value = Math.max(1, Math.ceil((viewport?.clientWidth ?? 0) / width))
  duration.value = Math.max(20, (repeats.value * width) / SPEED)
}

const load = async () => {
  const scoreboard = await getLatestScoreboard()
  if (disposed) return
  games.value = scoreboard.games
  live.value = scoreboard.live
  gameDate.value = scoreboard.gameDate
  await nextTick()
  if (disposed) return
  updateLayout()
  scheduleRefresh()
}

const scheduleRefresh = () => {
  clearTimeout(refreshTimer)
  if (disposed) return
  refreshTimer = setTimeout(load, live.value ? LIVE_REFRESH_MS : IDLE_REFRESH_MS)
}

const onMotionChange = (event) => {
  reducedMotion.value = event.matches
}

onMounted(() => {
  motionQuery = window.matchMedia('(prefers-reduced-motion: reduce)')
  reducedMotion.value = motionQuery.matches
  motionQuery.addEventListener('change', onMotionChange)
  window.addEventListener('resize', updateLayout)
  load()
})

onUnmounted(() => {
  disposed = true
  clearTimeout(refreshTimer)
  motionQuery?.removeEventListener('change', onMotionChange)
  window.removeEventListener('resize', updateLayout)
})
</script>

<style scoped>
.score-ticker {
  --color-ticker-highlight: hsl(0, 85%, 62%);
  display: flex;
  align-items: stretch;
  border-bottom: var(--color-border-thick) solid var(--color-border);
  background-color: var(--color-bg-card);
  font-size: 0.85rem;
  overflow: hidden;
}

.ticker-label {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  padding: 0.5rem 1rem;
  border-right: var(--color-border-thick) solid var(--color-border);
  color: var(--color-text-secondary);
}

.ticker-viewport {
  flex: 1;
  min-width: 0;
  overflow: hidden;
}

.ticker-track {
  display: flex;
  width: max-content;
  animation: ticker-scroll linear infinite;
}

.score-ticker:hover .ticker-track,
.score-ticker:focus-within .ticker-track {
  animation-play-state: paused;
}

.ticker-content {
  display: flex;
  flex-shrink: 0;
  white-space: nowrap;
}

.ticker-game {
  display: flex;
  align-items: center;
  padding: 0.5rem 0;
}

/* Separates consecutive games, including the end of one copy and the start of the next */
.ticker-game::after {
  content: '|';
  padding: 0 1.5rem;
  color: var(--color-border);
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

@keyframes ticker-scroll {
  from {
    transform: translateX(0);
  }
  to {
    transform: translateX(-50%);
  }
}

/* No motion: the line stays still and can be scrolled sideways */
.reduced-motion .ticker-viewport {
  overflow-x: auto;
}

.reduced-motion .ticker-track {
  animation: none;
}

@media (max-width: 768px) {
  .score-ticker {
    font-size: 0.75rem;
  }

  .ticker-label {
    padding: 0.5rem 0.75rem;
  }
}
</style>
