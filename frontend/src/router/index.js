import { createRouter, createWebHistory } from 'vue-router'
import { applySeo, routeSeo } from '../seo.js'
import HomePage from '../views/HomePage.vue'
import PlayerPage from '../views/PlayerPage.vue'
import TeamPage from '../views/TeamPage.vue'
import SettingsPage from '../views/SettingsPage.vue'
import FeedbackPage from '../views/FeedbackPage.vue'
import DonationsPage from '../views/DonationsPage.vue'
import TipsPage from '../views/TipsPage.vue'

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
  },
  {
    path: '/feedback',
    name: 'Feedback',
    component: FeedbackPage
  },
  {
    path: '/donations',
    name: 'Donations',
    component: DonationsPage
  },
  {
    path: '/tips',
    name: 'Tips',
    component: TipsPage
  },
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

router.afterEach((to) => applySeo(routeSeo(to.path)))

export default router

