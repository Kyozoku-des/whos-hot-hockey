<template>
  <div
    v-if="games.length"
    class="score-ticker"
    :class="{ 'reduced-motion': reducedMotion, held }"
    :aria-label="ariaLabel"
    role="marquee"
    @pointerenter="onPointerEnter"
    @pointerleave="hovered = false"
    @pointerdown="onPointerDown"
    @pointermove="onPointerMove"
    @pointerup="onPointerUp"
    @pointercancel="onPointerUp"
    @focusin="focused = true"
    @focusout="focused = false"
  >
    <div class="ticker-label">{{ live ? 'LIVE' : dayLabel }}</div>
    <div class="ticker-viewport" :ref="setViewport">
      <!-- Two equal halves, each wide enough to fill the line, so the loop scrolls seamlessly;
           only the first copy is read by screen readers -->
      <div class="ticker-track" :ref="setTrack">
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
// After a drag the line glides at the release speed and eases back to SPEED over about this long
const EASE_SECONDS = 0.8
// Fastest fling, in pixels per second
const MAX_FLING = 4000
// A release this long after the last movement is a stop, not a fling
const FLING_WINDOW_MS = 100

const { getLatestScoreboard } = useGameScores()

const games = ref([])
const live = ref(false)
const gameDate = ref(null)
// Copies of the content in each half of the track
const repeats = ref(1)
const reducedMotion = ref(false)
// Dragged by hand while pressed
const held = ref(false)
// Paused while a mouse is over the line or it has keyboard focus. Only mouse hover
// counts: touch screens keep hover after a tap, which would leave the ticker stopped
const hovered = ref(false)
const focused = ref(false)

// Measured to fill the line and loop the scroll
let content = null
let viewport = null
let track = null
const setContent = (el) => {
  content = el
}
const setViewport = (el) => {
  viewport = el
}
const setTrack = (el) => {
  track = el
}

// How far the line has scrolled left, in pixels, within one half of the track
let offset = 0
// Current speed in pixels per second; positive scrolls left
let velocity = SPEED
// Width of one half of the track, after which the scroll loops
let loopWidth = 0
let frame = null
let lastFrame = null
// Pointer position and time at the last drag movement
let dragX = 0
let dragTime = 0
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

// Repeat short content so no gap shows
const updateLayout = () => {
  const width = content?.offsetWidth
  if (!width) return
  repeats.value = Math.max(1, Math.ceil((viewport?.clientWidth ?? 0) / width))
  loopWidth = repeats.value * width
}

const render = () => {
  if (!track) return
  if (loopWidth) offset = ((offset % loopWidth) + loopWidth) % loopWidth
  track.style.transform = `translate3d(${-offset}px, 0, 0)`
}

// Move the line each frame; after a drag, ease from the release speed back to the
// normal speed, or to a stop while paused
const step = (time) => {
  frame = requestAnimationFrame(step)
  // A long gap (a background tab) would otherwise jump the line
  const seconds = lastFrame === null ? 0 : Math.min(time - lastFrame, 100) / 1000
  lastFrame = time
  if (held.value) return
  const target = hovered.value || focused.value ? 0 : SPEED
  velocity += (target - velocity) * (1 - Math.exp(-seconds / (EASE_SECONDS / 3)))
  offset += velocity * seconds
  render()
}

const startMotion = () => {
  if (frame !== null || reducedMotion.value) return
  lastFrame = null
  frame = requestAnimationFrame(step)
}

const stopMotion = () => {
  if (frame !== null) cancelAnimationFrame(frame)
  frame = null
  if (track) track.style.transform = ''
}

const onPointerEnter = (event) => {
  if (event.pointerType === 'mouse') hovered.value = true
}

// Holding the line lets it be dragged sideways; letting go flings it
const onPointerDown = (event) => {
  if (reducedMotion.value || (event.pointerType === 'mouse' && event.button !== 0)) return
  held.value = true
  velocity = 0
  dragX = event.clientX
  dragTime = event.timeStamp
  event.currentTarget.setPointerCapture?.(event.pointerId)
}

const onPointerMove = (event) => {
  if (!held.value) return
  const moved = dragX - event.clientX
  const elapsed = event.timeStamp - dragTime
  offset += moved
  render()
  if (elapsed > 0) {
    // Smooth over the last few movements so one jittery event doesn't set the fling
    velocity = 0.8 * ((moved / elapsed) * 1000) + 0.2 * velocity
  }
  dragX = event.clientX
  dragTime = event.timeStamp
}

const onPointerUp = (event) => {
  if (!held.value) return
  held.value = false
  velocity = event.timeStamp - dragTime > FLING_WINDOW_MS
    ? 0
    : Math.max(-MAX_FLING, Math.min(MAX_FLING, velocity))
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
  render()
  startMotion()
  scheduleRefresh()
}

const scheduleRefresh = () => {
  clearTimeout(refreshTimer)
  if (disposed) return
  refreshTimer = setTimeout(load, live.value ? LIVE_REFRESH_MS : IDLE_REFRESH_MS)
}

const onMotionChange = async (event) => {
  reducedMotion.value = event.matches
  if (event.matches) {
    stopMotion()
  } else {
    // The extra copies are rendered again before the loop can be measured
    await nextTick()
    updateLayout()
    startMotion()
  }
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
  stopMotion()
  motionQuery?.removeEventListener('change', onMotionChange)
  window.removeEventListener('resize', updateLayout)
})
</script>

<style scoped>
.score-ticker {
  --color-ticker-highlight: var(--color-negative, hsl(0, 85%, 62%));
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
  will-change: transform;
}

/* Horizontal drags move the line; vertical swipes still scroll the page */
.score-ticker:not(.reduced-motion) {
  cursor: grab;
  touch-action: pan-y;
}

.score-ticker.held {
  cursor: grabbing;
}

/* Dragging the line shouldn't select text or open a menu */
.score-ticker:not(.reduced-motion),
.score-ticker:not(.reduced-motion) * {
  user-select: none;
  -webkit-user-select: none;
  -webkit-touch-callout: none;
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

/* No motion: the line stays still and can be scrolled sideways */
.reduced-motion .ticker-viewport {
  overflow-x: auto;
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
