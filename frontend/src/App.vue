<template>
  <div id="app">
    <header class="app-header">
      <div class="container">
        <router-link to="/" class="logo">NHL Tracker</router-link>
      </div>
    </header>

    <main class="app-main">
      <router-view />
    </main>

    <footer class="app-footer">
      <div class="container">
        <p>Stats updated</p>
      </div>
    </footer>
  </div>
</template>

<script setup>
</script>

<style scoped>
.app-header {
  background-color: var(--color-header);
  color: var(--color-text-secondary);
  padding: 1.5rem 0;
}

.app-header .container {
  max-width: 1400px;
  margin: 0 auto;
  padding: 0 2rem;
}

.logo {
  font-size: 1.75rem;
  font-weight: 700;
  color: var(--color-text-secondary);
  text-decoration: none;
}

.app-main {
  flex: 1;
  padding: 2rem 0;
}

.app-footer {
  background-color: var(--color-bg-dark);
  color: var(--color-text-secondary);
  padding: 1rem 0;
  text-align: center;
}

.container {
  max-width: 1400px;
  margin: 0 auto;
  padding: 0 2rem;
}
</style>
