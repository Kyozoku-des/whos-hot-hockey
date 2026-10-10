import { createRouter, createWebHistory } from 'vue-router'
import HomePage from '../views/HomePage.vue'
import PlayerPage from '../views/PlayerPage.vue'
import TeamPage from '../views/TeamPage.vue'
import SettingsPage from '../views/SettingsPage.vue'
import FeedbackPage from '../views/FeedbackPage.vue'

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
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
