import { createApp } from 'vue'
import { createPinia } from 'pinia'
import './style.css'
import './themes/color-schemes.css'
import App from './App.vue'
import router from './router'

const pinia = createPinia()

createApp(App)
  .use(pinia)
  .use(router)
  .mount('#app')
