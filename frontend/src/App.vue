<template>
  <div id="app">
    <main class="app-main">
      <router-view />
    </main>

    <nav class="site-links">
      <router-link to="/settings">Settings</router-link>
      <router-link to="/feedback">Feedback</router-link>
    </nav>

    <footer class="app-footer">
      <div class="container">
        <p>Stats last updated: {{ lastUpdated }}</p>
      </div>
    </footer>

    <NextGameTooltip />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import NextGameTooltip from './components/NextGameTooltip.vue'
import { timeZoneOption } from './composables/useSettings'

const loadedAt = new Date()

// Shown in the time zone picked in settings
const lastUpdated = computed(() => {
  const options = {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    timeZoneName: 'short',
    timeZone: timeZoneOption()
  }
  return loadedAt.toLocaleString('en-US', options)
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
  border-top: var(--color-border-thick) solid var(--color-border);
  color: var(--color-text-primary);
  padding: 1rem 0;
  text-align: center;
  z-index: 100;
}

.app-footer p {
  margin: 0;
  font-size: 0.9rem;
  letter-spacing: 1px;
  color: #7dff7d;
}

.container {
  max-width: 1400px;
  margin: 0 auto;
  padding: 0 2rem;
}
</style>
