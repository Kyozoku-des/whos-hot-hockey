<template>
  <TickerLine
    class="watch-ticker"
    :items="items"
    :item-key="item => `${item.type}-${item.id}`"
    label="WATCH"
    aria-label="Players and teams to watch"
    :speed="SPEED"
  >
    <template #default="{ item, copy }">
      <router-link
        class="watch-item"
        :class="item.direction === 'UP' ? 'up' : 'down'"
        :to="link(item)"
        :title="title(item)"
        :tabindex="copy > 0 ? -1 : undefined"
        draggable="false"
      >
        <span class="watch-arrow" role="img" :aria-label="item.direction === 'UP' ? 'Up' : 'Down'">
          {{ item.direction === 'UP' ? '▲' : '▼' }}
        </span>
        <span class="watch-name">{{ item.name }}</span>
        <span v-if="item.type === 'PLAYER'" class="watch-team">{{ item.teamCode }}</span>
        <span class="watch-recent">{{ recent(item) }}</span>
        <span class="watch-baseline">vs {{ rate(item, item.baseline) }} {{ RULES[item.rule] }}</span>
      </router-link>
    </template>
  </TickerLine>
</template>

<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
import { useWatchlist } from '../composables/useApi'
import TickerLine from './TickerLine.vue'

// Form changes at most once a game day; refresh every ten minutes like the idle score ticker
const REFRESH_MS = 10 * 60 * 1000
// Slower than the score ticker above, so the two lines read as separate
const SPEED = 35

const RULES = {
  LAST_SEASON: 'last season',
  EARLIER_THIS_SEASON: 'earlier this season',
  LEAGUE: 'league average'
}

const { getWatchlist } = useWatchlist()

const items = ref([])

let refreshTimer = null
// Set on unmount so a request still in flight cannot start a new refresh loop
let disposed = false

const link = (item) => (item.type === 'PLAYER' ? `/player/${item.id}` : `/team/${item.id}`)

// Players by points per game, teams by point percentage written the hockey way (.625)
const rate = (item, value) =>
  item.type === 'PLAYER' ? value.toFixed(2) : value.toFixed(3).replace(/^0(?=\.)/, '')

const recent = (item) =>
  `${rate(item, item.recent)} ${item.type === 'PLAYER' ? 'P/GP' : 'P%'} last ${item.recentGames}`

const title = (item) => {
  const direction = item.direction === 'UP' ? 'above' : 'below'
  return `${item.score.toFixed(1)} standard deviations ${direction} ${RULES[item.rule]}`
}

const load = async () => {
  const watchlist = await getWatchlist()
  if (disposed) return
  items.value = watchlist.items
  clearTimeout(refreshTimer)
  refreshTimer = setTimeout(load, REFRESH_MS)
}

onMounted(load)

onUnmounted(() => {
  disposed = true
  clearTimeout(refreshTimer)
})
</script>

<style scoped>
/* Darker line with a solid label, so it stands apart from the score ticker above */
.watch-ticker {
  --ticker-bg: var(--color-bg-dark);
  --ticker-label-bg: var(--color-chip-bg);
  --ticker-label-color: var(--color-chip-fg);
}

.watch-item {
  display: inline-flex;
  align-items: baseline;
  gap: 0.4rem;
  color: var(--color-text-primary);
  text-decoration: none;
}

.watch-item:hover .watch-name,
.watch-item:focus-visible .watch-name {
  text-decoration: underline;
}

.watch-arrow {
  font-size: 0.8em;
}

.up .watch-arrow,
.up .watch-recent {
  color: var(--color-positive, #4ade80);
}

.down .watch-arrow,
.down .watch-recent {
  color: var(--color-negative, #f87171);
}

.watch-team,
.watch-baseline {
  opacity: 0.6;
}
</style>
