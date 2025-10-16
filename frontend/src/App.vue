<template>
  <div id="app">
    <header class="app-header">
      <div class="container">
        <router-link to="/" class="logo">
          <img src="./assets/nhl_logo.png" alt="Who's Hot NHL" class="logo-image" />
        </router-link>
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
  display: inline-block;
  text-decoration: none;
  line-height: 0;
}

.logo-image {
  height: 80px;
  width: auto;
  display: block;
  transform: scaleX(0.8);
  transform-origin: left center;
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
