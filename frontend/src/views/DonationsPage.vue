<template>
  <InfoPage title="Donations">
    <section class="info-section">
      <h2>Support the site</h2>
      <p>
        Whos Hot NHL is free and ad-free. If you enjoy it, a donation helps cover
        hosting and keeps new features coming.
      </p>
      <template v-if="DONATION_URL">
        <a class="donate-button" :href="DONATION_URL" target="_blank" rel="noopener">Donate</a>
        <p class="info-note">Payments are handled securely by Stripe. No card details pass through this site.</p>
      </template>
      <p v-else class="info-note">Donations are not open yet. Check back soon!</p>
    </section>
  </InfoPage>
</template>

<script setup>
import InfoPage from '../components/InfoPage.vue'

// A Stripe Payment Link, set at build time. Stripe hosts the checkout, so the
// site needs no payment code or keys of its own.
const DONATION_URL = import.meta.env.VITE_DONATION_URL || ''
</script>

<style scoped>
.donate-button {
  display: inline-block;
  margin: 1.25rem 0 1rem;
  padding: 0.75rem 2rem;
  font-size: 1rem;
  color: #000;
  background-color: #FFAA00;
  border-radius: 8px;
  transition: background-color 0.2s ease, transform 0.2s ease;
}

.donate-button:hover {
  background-color: #FFB820;
  transform: translateY(-2px);
}
</style>
