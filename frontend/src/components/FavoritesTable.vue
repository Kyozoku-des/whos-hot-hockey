<template>
  <div class="favorites-list">
    <div v-if="favorites.length === 0" class="empty-state">
      <div class="empty-icon">⭐</div>
      <h3>No favorites yet</h3>
      <p>Click the ★ icon on any player or team to add them here!</p>
    </div>

    <div v-else-if="loading" class="loading">Loading favorites...</div>

    <div v-else class="favorites-grid">
      <!-- Player Favorites -->
      <div
        v-for="player in playerFavorites"
        :key="player.playerId"
        class="player-item"
        @click="goToPlayer(player.playerId)"
      >
        <button
          class="remove-btn"
          @click.stop="handleRemove(player.playerId)"
          title="Remove from favorites"
        >
          ✕
        </button>
        <div class="player-main">
          <TeamLogo :logoUrl="player.teamLogoUrl" :teamCode="player.teamCode" size="small" />
          <span class="player-name">{{ player.firstName }} {{ player.lastName }}</span>
        </div>
        <span class="player-stats">
          <span class="stat-item">G: {{ player.goals }}</span>
          <span class="stat-item">A: {{ player.assists }}</span>
          <span class="stat-item">P: {{ player.points }}</span>
          <span class="stat-item">GP: {{ player.gamesPlayed }}</span>
        </span>
      </div>

      <!-- Team Favorites -->
      <div
        v-for="team in teamFavorites"
        :key="team.teamCode"
        class="team-item"
        @click="goToTeam(team.teamCode)"
      >
        <button
          class="remove-btn"
          @click.stop="handleRemove(team.teamCode)"
          title="Remove from favorites"
        >
          ✕
        </button>
        <div class="team-main">
          <TeamLogo :logoUrl="team.logoUrl" :teamCode="team.teamCode" :alt="team.teamName" size="small" />
          <span class="team-name">{{ team.teamName }}</span>
        </div>
        <span class="team-stats">
          <span class="stat-item">GP: {{ team.gamesPlayed }}</span>
          <span class="stat-item">W: {{ team.wins }}</span>
          <span class="stat-item">L: {{ team.losses }}</span>
          <span class="stat-item stat-points">PTS: {{ team.points }}</span>
        </span>
      </div>
    </div>

    <div v-if="favorites.length > 0" class="favorites-count">
      {{ favorites.length }} / {{ maxFavorites }} favorites
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useFavorites } from '../composables/useFavorites'
import { usePlayerStats, useTeamStats } from '../composables/useApi'
import TeamLogo from './TeamLogo.vue'

const router = useRouter()
const { favorites, removeFavorite } = useFavorites()
const { loading: playersLoading, getTopScorers } = usePlayerStats()
const { loading: teamsLoading, getStandings } = useTeamStats()

const maxFavorites = 10
const allPlayers = ref([])
const allTeams = ref([])

// Fetch player and team data
const loadData = async () => {
  const [playersData, teamsData] = await Promise.all([
    getTopScorers(100), // Fetch more players to ensure we get all favorites
    getStandings()
  ])

  if (playersData) allPlayers.value = playersData
  if (teamsData) allTeams.value = teamsData
}

onMounted(() => {
  loadData()
})

// Reload data when favorites change
watch(() => favorites.value.length, () => {
  if (favorites.value.length > 0 && (allPlayers.value.length === 0 || allTeams.value.length === 0)) {
    loadData()
  }
})

// Get player favorites with full data
const playerFavorites = computed(() => {
  return favorites.value
    .filter(fav => fav.type === 'PLAYER')
    .map(fav => {
      const player = allPlayers.value.find(p => p.playerId === fav.id)
      return player || null
    })
    .filter(p => p !== null)
})

// Get team favorites with full data
const teamFavorites = computed(() => {
  return favorites.value
    .filter(fav => fav.type === 'TEAM')
    .map(fav => {
      const team = allTeams.value.find(t => t.teamCode === fav.id)
      return team || null
    })
    .filter(t => t !== null)
})

const loading = computed(() => playersLoading.value || teamsLoading.value)

const handleRemove = (id) => {
  removeFavorite(id)
}

const goToPlayer = (playerId) => {
  router.push(`/player/${playerId}`)
}

const goToTeam = (teamCode) => {
  router.push(`/team/${teamCode}`)
}
</script>

<style scoped>
.favorites-list {
  width: 100%;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 3rem 2rem;
  text-align: center;
}

.empty-icon {
  font-size: 4rem;
  margin-bottom: 1rem;
  opacity: 0.3;
}

.empty-state h3 {
  font-size: 1.5rem;
  font-weight: 600;
  color: var(--color-text-primary);
  margin: 0 0 0.5rem 0;
}

.empty-state p {
  font-size: 1rem;
  color: var(--color-text-secondary);
  margin: 0;
}

.favorites-grid {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  padding-right: 1.5rem;
}

/* Player Item Styles (matching TopPointsTable) */
.player-item {
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

.player-item:hover {
  background-color: rgba(255, 255, 255, 0.1);
  transform: translateX(5px);
  z-index: 10;
}

.player-main {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  flex: 1;
  padding-left: 2.5rem;
}

.player-headshot {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  object-fit: cover;
  border: 2px solid var(--color-border);
}

.player-name {
  color: var(--color-text-secondary);
  font-size: 1rem;
  font-weight: bold;
  flex: 1;
}

.player-stats {
  display: flex;
  gap: 1rem;
  align-items: center;
}

/* Team Item Styles (matching TeamStandingsTable) */
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

.team-name {
  color: var(--color-text-secondary);
  font-size: 1rem;
  font-weight: bold;
  flex: 1;
}

.team-stats {
  display: flex;
  gap: 1rem;
  align-items: center;
}

/* Shared Styles */
.stat-item {
  color: var(--color-text-primary);
  font-size: 0.9rem;
  font-weight: normal;
  white-space: nowrap;
}

.stat-points {
  font-weight: bold;
}

.remove-btn {
  position: absolute;
  left: 0.5rem;
  top: 50%;
  transform: translateY(-50%);
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  color: #ff6b6b;
  font-size: 1.2rem;
  font-weight: bold;
  cursor: pointer;
  transition: all 0.2s ease;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 5;
}

.remove-btn:hover {
  color: #ff5252;
  filter: drop-shadow(0 0 8px rgba(255, 107, 107, 0.8));
  text-shadow: 0 0 10px rgba(255, 107, 107, 0.6);
}

.favorites-count {
  text-align: center;
  padding: 1rem;
  font-size: 0.9rem;
  color: var(--color-text-secondary);
  font-weight: 600;
}

.loading {
  text-align: center;
  padding: 2rem;
  color: var(--color-text-secondary);
}
</style>
