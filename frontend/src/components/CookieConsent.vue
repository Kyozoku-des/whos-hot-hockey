<template>
  <Teleport to="body">
    <Transition name="slide-up">
      <div v-if="showBanner" class="cookie-consent-banner">
        <div class="banner-container">
          <div class="banner-content">
            <div class="banner-icon">🍪</div>
            <div class="banner-text">
              <h3>Save Your Favorites</h3>
              <p>
                We use browser storage to remember your favorite teams and players.
                Would you like to enable this feature? Your favorites will be saved across sessions.
              </p>
            </div>
          </div>
          <div class="banner-actions">
            <button @click="decline" class="btn-decline">
              No Thanks
            </button>
            <button @click="accept" class="btn-accept">
              Enable Favorites
            </button>
          </div>
          <button @click="decline" class="btn-close" title="Close">✕</button>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useFavorites } from '../composables/useFavorites'

const { acceptConsent, declineConsent, checkConsent } = useFavorites()
const showBanner = ref(false)

const accept = () => {
  acceptConsent()
  showBanner.value = false
}

const decline = () => {
  declineConsent()
  showBanner.value = false
}

onMounted(() => {
  // Show banner only if consent hasn't been given or declined
  const consent = checkConsent()
  if (consent === null) {
    // Small delay for better UX
    setTimeout(() => {
      showBanner.value = true
    }, 1000)
  }
})
</script>

<style scoped>
.cookie-consent-banner {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  z-index: 9999;
  padding: 1rem;
  background: linear-gradient(to top, rgba(0, 0, 0, 0.95), rgba(0, 0, 0, 0.9));
  backdrop-filter: blur(10px);
  border-top: 2px solid var(--color-border);
  box-shadow: 0 -4px 20px rgba(0, 0, 0, 0.3);
}

.banner-container {
  max-width: 1400px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  gap: 2rem;
  position: relative;
}

.banner-content {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 1.5rem;
}

.banner-icon {
  font-size: 2.5rem;
  flex-shrink: 0;
}

.banner-text h3 {
  margin: 0 0 0.5rem 0;
  font-size: 1.2rem;
  font-weight: 700;
  color: var(--color-text-primary);
}

.banner-text p {
  margin: 0;
  font-size: 0.95rem;
  color: var(--color-text-secondary);
  line-height: 1.5;
}

.banner-actions {
  display: flex;
  gap: 1rem;
  flex-shrink: 0;
}

.btn-accept,
.btn-decline {
  padding: 0.75rem 1.5rem;
  font-size: 1rem;
  font-weight: 600;
  font-family: var(--font-family);
  border-radius: 8px;
  border: none;
  cursor: pointer;
  transition: all 0.2s ease;
  white-space: nowrap;
}

.btn-accept {
  background-color: #FFAA00;
  color: #000;
}

.btn-accept:hover {
  background-color: #FFB820;
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(255, 170, 0, 0.3);
}

.btn-decline {
  background-color: transparent;
  color: var(--color-text-secondary);
  border: 2px solid var(--color-border);
}

.btn-decline:hover {
  background-color: rgba(255, 255, 255, 0.05);
  border-color: var(--color-text-secondary);
}

.btn-close {
  position: absolute;
  top: 0;
  right: 0;
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  color: var(--color-text-secondary);
  font-size: 1.5rem;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  transition: all 0.2s ease;
}

.btn-close:hover {
  background-color: rgba(255, 255, 255, 0.1);
  color: var(--color-text-primary);
}

/* Slide up transition */
.slide-up-enter-active,
.slide-up-leave-active {
  transition: transform 0.3s ease, opacity 0.3s ease;
}

.slide-up-enter-from {
  transform: translateY(100%);
  opacity: 0;
}

.slide-up-leave-to {
  transform: translateY(100%);
  opacity: 0;
}

@media (max-width: 968px) {
  .banner-container {
    flex-direction: column;
    align-items: stretch;
    gap: 1.5rem;
  }

  .banner-content {
    flex-direction: column;
    text-align: center;
  }

  .banner-actions {
    flex-direction: column;
    width: 100%;
  }

  .btn-accept,
  .btn-decline {
    width: 100%;
  }

  .btn-close {
    top: 1rem;
    right: 1rem;
  }
}
</style>
