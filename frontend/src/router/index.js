import { createRouter, createWebHistory } from 'vue-router'
import HomePage from '../views/HomePage.vue'
import PlayerPage from '../views/PlayerPage.vue'
import TeamPage from '../views/TeamPage.vue'
import SettingsPage from '../views/SettingsPage.vue'

const routes = [
  {
    path: '/',
    name: 'Home',
    component: HomePage
  },
  {
    path: '/player/:id',
    name: 'Player',
    component: PlayerPage
  },
  {
    path: '/team/:id',
    name: 'Team',
    component: TeamPage
  },
  {
    path: '/settings',
    name: 'Settings',
    component: SettingsPage
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  // New pages open at the top (the footer links sit at the very bottom); back
  // and forward return to where the page was left
  scrollBehavior(to, from, savedPosition) {
    return savedPosition || { top: 0 }
  }
})

export default router
