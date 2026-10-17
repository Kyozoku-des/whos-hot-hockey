import { createRouter, createWebHistory } from 'vue-router'
import { installSeoNavigation } from '../seo.js'
import { PAGE_ROUTES } from './routes.js'
import HomePage from '../views/HomePage.vue'

// The home page is in the main bundle, as most visits start there. The other
// pages load on first visit, so Chart.js only downloads with the player and
// team pages.
const components = {
  Home: HomePage,
  Player: () => import('../views/PlayerPage.vue'),
  Team: () => import('../views/TeamPage.vue'),
  Settings: () => import('../views/SettingsPage.vue'),
  Feedback: () => import('../views/FeedbackPage.vue'),
  Donations: () => import('../views/DonationsPage.vue'),
  Tips: () => import('../views/TipsPage.vue')
}
const routes = [
  ...PAGE_ROUTES.map(route => ({ ...route, component: components[route.name] })),
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('../views/NotFoundPage.vue')
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  // New pages open at the top (the site links sit at the very bottom); back
  // and forward return to where the page was left
  scrollBehavior(to, from, savedPosition) {
    return savedPosition || { top: 0 }
  }
})

installSeoNavigation(router)

export default router
