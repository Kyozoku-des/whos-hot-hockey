import { createRouter, createWebHistory } from 'vue-router'
import HomePage from '../views/HomePage.vue'
import PlayerPage from '../views/PlayerPage.vue'
import TeamPage from '../views/TeamPage.vue'

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
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
