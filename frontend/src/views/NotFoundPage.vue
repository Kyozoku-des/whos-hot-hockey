<template>
  <section class="not-found">
    <h1>Page not found</h1>
    <p>The page you requested could not be found.</p>
    <router-link to="/">Explore NHL stats on Who's Hot Hockey</router-link>
  </section>
</template>

<style scoped>
.not-found { padding: 3rem 1.5rem; text-align: center; }
a { color: var(--color-text-secondary); }
</style>
