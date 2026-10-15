import { createApp } from 'vue'
import { createPinia } from 'pinia'
import './style.css'
import './themes/color-schemes.css'
import App from './App.vue'
import router from './router'

// After a deploy, a page left open still asks for the previous build's page
// chunks, which are gone. Reload once to pick up the new build; the guard
// stops a reload loop when the chunk is missing for another reason.
const CHUNK_RELOAD_KEY = 'chunk_reload_at'
window.addEventListener('vite:preloadError', (event) => {
  try {
    const last = Number(sessionStorage.getItem(CHUNK_RELOAD_KEY))
    if (Date.now() - last < 10_000) return
    sessionStorage.setItem(CHUNK_RELOAD_KEY, String(Date.now()))
  } catch {
    return
  }
  event.preventDefault()
  window.location.reload()
})

const pinia = createPinia()

createApp(App)
  .use(pinia)
  .use(router)
  .mount('#app')
