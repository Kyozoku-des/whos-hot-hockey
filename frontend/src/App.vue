<template>
  <div id="app">
    <main class="app-main">
      <router-view />
    </main>

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
        <PixelIcon name="arrow-down" />
      </button>
    </Transition>

    <footer class="app-footer">
      <div class="container">
        <p>Stats last updated: {{ lastUpdated }}</p>
      </div>
    </footer>

    <NextGameTooltip />
  </div>
</template>

<script setup>
import { computed, ref, onMounted, onUnmounted } from 'vue'
import NextGameTooltip from './components/NextGameTooltip.vue'
import PixelIcon from './components/PixelIcon.vue'
import { timeZoneOption } from './composables/useSettings'
import { useApi } from './composables/useApi'

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

const siteLinks = ref(null)
// Starts true so the arrow doesn't flash before the first check
const linksVisible = ref(true)
let observer = null

const scrollToLinks = () => {
  siteLinks.value?.scrollIntoView({ behavior: 'smooth', block: 'end' })
}

onMounted(() => {
  loadStatus()
  statusTimer = setInterval(loadStatus, STATUS_REFRESH_MS)

  // The bottom margin leaves out the strip behind the fixed footer (rootMargin
  // only takes px or %)
  observer = new IntersectionObserver(([entry]) => {
    linksVisible.value = entry.isIntersecting
  }, { rootMargin: '0px 0px -64px 0px' })
  observer.observe(siteLinks.value)
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

/* Below all page content, clear of the fixed footer */
.site-links {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 0.5rem 2rem;
  padding: 2rem 1rem 6rem;
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
  bottom: 4.5rem;
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

.app-footer {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  background-color: var(--color-bg-card);
  border-top: var(--color-border-thick) solid var(--color-line);
  color: var(--color-text-primary);
  padding: 1rem 0;
  text-align: center;
  z-index: 100;
}

.app-footer p {
  margin: 0;
  font-size: 0.9rem;
  letter-spacing: calc(1 * var(--tracking));
  color: var(--color-success, #7dff7d);
}

.container {
  max-width: 1400px;
  margin: 0 auto;
  padding: 0 2rem;
}
</style>
