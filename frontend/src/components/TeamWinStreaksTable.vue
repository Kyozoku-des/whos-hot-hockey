<template>
  <div class="team-list">
    <div v-if="loading" class="loading">Loading...</div>
    <div v-else-if="error" class="error">{{ error }}</div>
    <div v-else-if="teams.length === 0" class="empty">No win streaks found</div>
    <div v-else class="teams-grid">
      <div
        v-for="(team, index) in teams"
        :key="team.teamCode"
        class="team-item"
        @click="goToTeam(team.teamCode)"
      >
        <button
          class="favorite-btn"
          @click.stop="toggleFavorite(team)"
          :disabled="!canFavorite(team)"
          :title="getFavoriteTooltip(team)"
        >
          {{ isFavorited(team.teamCode) ? '★' : '☆' }}
        </button>
        <div class="team-main">
          <TeamLogo :logoUrl="team.logoUrl" :teamCode="team.teamCode" :alt="team.teamName" size="small" />
          <span class="team-name">{{ team.teamName }}</span>
          <span class="team-icons">
            <img v-if="team.hot" src="../assets/flame.png" alt="Hot" class="status-icon" title="Hot streak" />
          </span>
        </div>
        <span class="team-stats">
          <span class="stat-item stat-streak">Streak: {{ team.currentWinStreak }}</span>
          <span v-if="isExpanded" class="stat-item">W: {{ team.wins }}</span>
          <span v-if="isExpanded" class="stat-item">L: {{ team.losses }}</span>
          <span v-if="isExpanded" class="stat-item">GP: {{ team.gamesPlayed }}</span>
          <span v-if="isExpanded" class="stat-item">PTS: {{ team.points }}</span>
        </span>
        <div class="tooltip" v-if="team.nextOpponentCode">
          Next: {{ formatNextGame(team) }}
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, inject, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useTeamStats } from '../composables/useApi'
import { useSearchStore } from '../stores/searchStore'
import { useFavorites } from '../composables/useFavorites'
import TeamLogo from './TeamLogo.vue'

const router = useRouter()
const { loading, error, getStandings } = useTeamStats()
const searchStore = useSearchStore()
const { isFavorited, toggleFavorite: toggleFav, canAddMore } = useFavorites()
const allTeams = ref([])
const isExpanded = inject('isExpanded', ref(false))

// Filter teams based on search query
const teams = computed(() => {
  const query = searchStore.currentQuery.toLowerCase().trim()
  let filtered = allTeams.value

  if (query) {
    filtered = allTeams.value.filter(team => {
      return team.teamName.toLowerCase().includes(query)
    })
  }

  // Sort by win streak length descending
  return filtered.sort((a, b) => b.currentWinStreak - a.currentWinStreak)
})

const loadData = async () => {
  const data = await getStandings()
  if (data) {
    allTeams.value = data
  }
}

onMounted(() => {
  loadData()
})

const goToTeam = (teamCode) => {
  router.push(`/team/${teamCode}`)
}

const formatNextGame = (team) => {
  if (!team.nextOpponentCode) return 'N/A'
  const location = team.nextGameIsHome ? 'vs' : '@'
  return `${location} ${team.nextOpponentCode}`
}

// Favorites functions
const toggleFavorite = (team) => {
  const favoriteData = {
    id: team.teamCode,
    type: 'TEAM',
    name: team.teamName,
    imageUrl: team.logoUrl || '',
    secondaryInfo: team.teamCode
  }
  toggleFav(favoriteData)
}

const canFavorite = (team) => {
  return isFavorited(team.teamCode) || canAddMore()
}

const getFavoriteTooltip = (team) => {
  if (isFavorited(team.teamCode)) {
    return 'Remove from favorites'
  }
  return canAddMore() ? 'Add to favorites' : 'Maximum 10 favorites reached'
}
</script>

<style scoped>
.team-list {
  width: 100%;
}

.teams-grid {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  padding-right: 1.5rem;
}

.team-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.75rem 1rem;
  background-color: rgba(255, 255, 255, 0.05);
  border: 2px solid var(--color-border);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
  position: relative;
}

.team-item:hover {
  background-color: rgba(255, 255, 255, 0.1);
  transform: translateX(5px);
  z-index: 10;
}

.team-main {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  flex: 1;
  padding-left: 2.5rem;
}

.team-rank {
  color: var(--color-text-primary);
  font-size: 1rem;
  font-weight: bold;
  min-width: 24px;
  text-align: center;
}

.team-name {
  color: var(--color-text-secondary);
  font-size: 1rem;
  font-weight: bold;
  flex: 1;
}

.team-icons {
  display: flex;
  gap: 0.5rem;
  align-items: center;
  margin-left: 0.5rem;
}

.status-icon {
  width: 20px;
  height: 20px;
  object-fit: contain;
}

.team-stats {
  display: flex;
  gap: 1rem;
  align-items: center;
  flex-wrap: wrap;
}

.stat-item {
  color: var(--color-text-primary);
  font-size: 0.9rem;
  font-weight: normal;
  white-space: nowrap;
}

.stat-streak {
  font-weight: bold;
  font-size: 1rem;
}

.tooltip {
  position: absolute;
  bottom: 100%;
  left: 50%;
  transform: translateX(-50%);
  background-color: white;
  color: black;
  padding: 0.5rem 1rem;
  border-radius: 6px;
  font-size: 0.85rem;
  font-weight: 600;
  white-space: nowrap;
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.2s;
  margin-bottom: 0.5rem;
  z-index: 9999;
  box-shadow: 0 4px 6px rgba(0, 0, 0, 0.3);
}

.team-item:hover .tooltip {
  opacity: 1;
}

.favorite-btn {
  position: absolute;
  left: 0.5rem;
  top: 50%;
  transform: translateY(-50%);
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  color: #FFD700;
  font-size: 1.5rem;
  cursor: pointer;
  transition: all 0.2s ease;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 5;
}

.favorite-btn:hover:not(:disabled) {
  filter: drop-shadow(0 0 8px rgba(255, 215, 0, 0.8));
  text-shadow: 0 0 10px rgba(255, 215, 0, 0.6);
}

/* Show filled star on hover for unfavorited items */
.favorite-btn:hover:not(:disabled)::after {
  content: '★';
  position: absolute;
  color: #FFD700;
  opacity: 0.7;
}

.favorite-btn:disabled {
  opacity: 0.3;
  cursor: not-allowed;
}

.loading,
.error,
.empty {
  text-align: center;
  padding: 2rem;
  color: var(--color-text-secondary);
}
</style>
