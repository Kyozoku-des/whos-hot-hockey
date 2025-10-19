<template>
  <div id="app">
    <main class="app-main">
      <router-view />
    </main>

    <footer class="app-footer">
      <div class="container">
        <p>Stats last updated: {{ lastUpdated }}</p>
      </div>
    </footer>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'

const lastUpdated = ref('')

const formatLastUpdated = () => {
  const now = new Date()
  const options = {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    timeZoneName: 'short'
  }
  lastUpdated.value = now.toLocaleString('en-US', options)
}

onMounted(() => {
  formatLastUpdated()
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
  padding-bottom: 5rem;
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
}

.container {
  max-width: 1400px;
  margin: 0 auto;
  padding: 0 2rem;
}
</style>
