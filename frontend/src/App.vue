<template>
  <div id="app">
    <main class="app-main">
      <router-view :key="$route.path" />
    </main>

    <p v-if="showStatsUpdated" class="stats-updated">Stats last updated: {{ lastUpdated }}</p>

    <nav ref="siteLinks" class="site-links">
      <router-link to="/settings">Settings</router-link>
      <router-link to="/feedback">Feedback</router-link>
      <router-link to="/donations">Donations</router-link>
      <router-link to="/tips">Tips</router-link>
    </nav>

    <!-- Hints that there is more below while the links are out of view.
         type="transition": the endless nudge animation would otherwise hold the leave -->
    <Transition name="fade" type="transition">
      <button
        v-if="!linksVisible"
        type="button"
        class="scroll-hint"
        aria-label="Scroll down to settings"
        @click="scrollToLinks"
      >
        <!-- Phones: just the arrowhead, in the slim gap the home tables leave -->
        <PixelIcon :name="isMobile ? 'chevron-down' : 'arrow-down'" />
      </button>
    </Transition>

    <NextGameTooltip />
  </div>
</template>

<script setup>
import { computed, ref, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import NextGameTooltip from './components/NextGameTooltip.vue'
import PixelIcon from './components/PixelIcon.vue'
import { timeZoneOption } from './composables/useSettings'
import { useIsMobile } from './composables/useIsMobile'
import { useApi } from './composables/useApi'

// Only the pages that show stats; the info pages leave it out
const STATS_PAGES = new Set(['Home', 'Player', 'Team'])
const route = useRoute()
const showStatsUpdated = computed(() => STATS_PAGES.has(route.name))

// When the data job last finished a sync, not when this page loaded; undefined until fetched
const statsUpdatedAt = ref(undefined)
const { fetchData } = useApi()
const loadStatus = async () => {
  const data = await fetchData('/status')
  if (data) {
    const updatedAt = data.statsUpdatedAt ? new Date(data.statsUpdatedAt) : null
    statsUpdatedAt.value = updatedAt && !Number.isNaN(updatedAt.getTime()) ? updatedAt : null
  } else if (statsUpdatedAt.value === undefined) {
    // A failed refresh keeps the last known time
    statsUpdatedAt.value = null
  }
}
// The site stays open for hours; refresh along with the syncs (polls run every minute in games)
const STATUS_REFRESH_MS = 5 * 60 * 1000
let statusTimer = null

// Shown in the time zone picked in settings
const lastUpdated = computed(() => {
  if (statsUpdatedAt.value === undefined) return '…'
  if (statsUpdatedAt.value === null) return 'unknown'
  const options = {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    timeZoneName: 'short',
    timeZone: timeZoneOption()
  }
  return statsUpdatedAt.value.toLocaleString('en-US', options)
})

const { isMobile } = useIsMobile()
const siteLinks = ref(null)
// Starts true so the arrow doesn't flash before the first check
const linksVisible = ref(true)
let observer = null

// Watches the first link itself, not the nav: the nav's top padding can peek
// out at the bottom of the screen while the links are still out of view
const observeLinks = () => {
  observer = new IntersectionObserver(([entry]) => {
    linksVisible.value = entry.isIntersecting
  })
  const firstLink = siteLinks.value?.firstElementChild
  if (firstLink) observer.observe(firstLink)
}

const scrollToLinks = () => {
  siteLinks.value?.scrollIntoView({ behavior: 'smooth', block: 'end' })
}

onMounted(() => {
  loadStatus()
  statusTimer = setInterval(loadStatus, STATUS_REFRESH_MS)
  observeLinks()
})

onUnmounted(() => {
  observer?.disconnect()
  clearInterval(statusTimer)
})
</script>

<style scoped>
#app {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}

.app-main {
  flex: 1;
  padding: 0;
}

/* Below all page content */
.stats-updated {
  margin: 2rem 0 0;
  padding: 0 1rem;
  text-align: center;
  font-size: 0.9rem;
  letter-spacing: calc(1 * var(--tracking));
  color: var(--color-success, #7dff7d);
}

.site-links {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 0.5rem 2rem;
  padding: 2rem 1rem;
  font-size: 0.9rem;
}

.site-links a {
  color: var(--color-text-secondary);
  text-decoration: underline;
  text-underline-offset: 4px;
}

.scroll-hint {
  position: fixed;
  left: 50%;
  bottom: 1rem;
  transform: translateX(-50%);
  z-index: 100;
  padding: 0.25rem;
  font-size: 1.75rem;
  color: var(--color-text-secondary);
  background: none;
  border: none;
  animation: nudge 1.6s ease-in-out infinite;
}

.scroll-hint:hover {
  color: var(--color-text-primary);
}

@keyframes nudge {
  0%, 100% { transform: translate(-50%, 0); }
  50% { transform: translate(-50%, 6px); }
}

@media (max-width: 768px) {
  /* In the slim gap the home tables leave at the bottom of the screen */
  .scroll-hint {
    bottom: 0.75rem;
    padding: 0;
    font-size: 1.1rem;
  }
}

@media (prefers-reduced-motion: reduce) {
  .scroll-hint {
    animation: none;
  }
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.site-links a:hover,
.site-links a.router-link-active {
  color: var(--color-text-primary);
}
</style>
