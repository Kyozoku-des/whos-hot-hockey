<template>
  <div id="app" :style="{ '--footer-height': `${footerHeight}px` }">
    <main class="app-main">
      <router-view :key="$route.path" />
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
        <!-- Phones: just the arrowhead, in the slim gap the home tables leave -->
        <PixelIcon :name="isMobile ? 'chevron-down' : 'arrow-down'" />
      </button>
    </Transition>

    <footer ref="footer" class="app-footer">
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
import { useIsMobile } from './composables/useIsMobile'
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

const { isMobile } = useIsMobile()
const siteLinks = ref(null)
const footer = ref(null)
const footerHeight = ref(64)
// Starts true so the arrow doesn't flash before the first check
const linksVisible = ref(true)
let observer = null
let footerObserver = null

// Watches the first link itself, not the nav: the nav's top padding can peek
// out above the footer while the links are still hidden behind it. The bottom
// margin leaves out the strip behind the fixed footer, so it is rebuilt when
// the footer's height changes (rootMargin can't be updated in place).
const observeLinks = () => {
  observer?.disconnect()
  observer = new IntersectionObserver(([entry]) => {
    linksVisible.value = entry.isIntersecting
  }, { rootMargin: `0px 0px -${footerHeight.value}px 0px` })
  const firstLink = siteLinks.value?.firstElementChild
  if (firstLink) observer.observe(firstLink)
}

const scrollToLinks = () => {
  siteLinks.value?.scrollIntoView({ behavior: 'smooth', block: 'end' })
}

onMounted(() => {
  loadStatus()
  statusTimer = setInterval(loadStatus, STATUS_REFRESH_MS)

  // The footer wraps to two lines on narrow phones once the time has loaded
  footerObserver = new ResizeObserver(() => {
    const height = footer.value.offsetHeight
    if (height === footerHeight.value) return
    footerHeight.value = height
    observeLinks()
  })
  footerObserver.observe(footer.value)
  observeLinks()
})

onUnmounted(() => {
  footerObserver?.disconnect()
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

@media (max-width: 768px) {
  /* Just above the footer, in the gap the home tables leave for it */
  .scroll-hint {
    bottom: calc(var(--footer-height) + 0.75rem);
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

