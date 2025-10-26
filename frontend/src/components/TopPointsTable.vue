<template>
  <div class="player-list">
    <div v-if="loading" class="loading">Loading...</div>
    <div v-else-if="error" class="error">{{ error }}</div>
    <div v-else-if="players.length === 0" class="empty">No players found</div>
    <div v-else class="players-grid">
      <div
        v-for="player in players"
        :key="player.playerId"
        class="player-item"
        @click="goToPlayer(player.playerId)"
      >
        <button
          class="favorite-btn"
          @click.stop="toggleFavorite(player)"
          :disabled="!canFavorite(player)"
          :title="getFavoriteTooltip(player)"
        >
          {{ isFavorited(player.playerId) ? '★' : '☆' }}
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
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { usePlayerStats } from '../composables/useApi'
import { useSearchStore } from '../stores/searchStore'
import { useFavorites } from '../composables/useFavorites'
import TeamLogo from './TeamLogo.vue'

const router = useRouter()
const { loading, error, getTopScorers } = usePlayerStats()
const searchStore = useSearchStore()
const { isFavorited, toggleFavorite: toggleFav, canAddMore } = useFavorites()
const allPlayers = ref([])

// Filter players based on search query
const players = computed(() => {
  const query = searchStore.currentQuery.toLowerCase().trim()
  if (!query) return allPlayers.value

  // Find teams matching the query
  const matchingTeamCodes = searchStore.searchData
    .filter(item => item.type === 'TEAM' && item.name.toLowerCase().includes(query))
    .map(team => team.id)

  return allPlayers.value.filter(player => {
    // Check if player name matches
    const fullName = `${player.firstName} ${player.lastName}`.toLowerCase()
    if (fullName.includes(query)) return true

    // Check if player's team matches
    if (player.teamCode && matchingTeamCodes.includes(player.teamCode)) return true

    return false
  })
})

const loadData = async () => {
  const data = await getTopScorers(5)
  if (data) {
    allPlayers.value = data
  }
}

onMounted(() => {
  loadData()
})

const goToPlayer = (playerId) => {
  router.push(`/player/${playerId}`)
}

// Favorites functions
const toggleFavorite = (player) => {
  const favoriteData = {
    id: player.playerId,
    type: 'PLAYER',
    name: `${player.firstName} ${player.lastName}`,
    imageUrl: player.headshotUrl || '',
    secondaryInfo: player.positionCode || ''
  }
  toggleFav(favoriteData)
}

const canFavorite = (player) => {
  return isFavorited(player.playerId) || canAddMore()
}

const getFavoriteTooltip = (player) => {
  if (isFavorited(player.playerId)) {
    return 'Remove from favorites'
  }
  return canAddMore() ? 'Add to favorites' : 'Maximum 10 favorites reached'
}
</script>

<style scoped>
.player-list {
  width: 100%;
}

.players-grid {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  padding-right: 1.5rem;
}

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

.player-name {
  color: var(--color-text-secondary);
  font-size: 1rem;
  font-weight: bold;
  flex: 1;
}

.team-code {
  color: var(--color-text-primary);
  font-size: 0.85rem;
  font-weight: 600;
  opacity: 0.8;
}

.player-stats {
  display: flex;
  gap: 1rem;
  align-items: center;
}

.stat-item {
  color: var(--color-text-primary);
  font-size: 0.9rem;
  font-weight: normal;
  white-space: nowrap;
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
