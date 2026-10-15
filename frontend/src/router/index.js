import { createRouter, createWebHistory } from 'vue-router'
import { installSeoNavigation } from '../seo.js'
import { PAGE_ROUTES } from './routes.js'
import HomePage from '../views/HomePage.vue'
import PlayerPage from '../views/PlayerPage.vue'
import TeamPage from '../views/TeamPage.vue'
import SettingsPage from '../views/SettingsPage.vue'
import FeedbackPage from '../views/FeedbackPage.vue'
import DonationsPage from '../views/DonationsPage.vue'
import TipsPage from '../views/TipsPage.vue'

const components = {
  Home: HomePage, Player: PlayerPage, Team: TeamPage, Settings: SettingsPage,
  Feedback: FeedbackPage, Donations: DonationsPage, Tips: TipsPage
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
  // New pages open at the top (the footer links sit at the very bottom); back
  // and forward return to where the page was left
  scrollBehavior(to, from, savedPosition) {
    return savedPosition || { top: 0 }
  }
})

installSeoNavigation(router)

export default router

