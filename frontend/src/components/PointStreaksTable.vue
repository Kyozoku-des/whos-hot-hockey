<template>
  <div class="player-list">
    <div v-if="loading" class="loading">Loading...</div>
    <div v-else-if="error" class="error">{{ error }}</div>
    <div v-else-if="players.length === 0" class="empty">No point streaks</div>
    <div v-else class="players-grid">
      <div
        v-for="player in players"
        :key="player.playerId"
        class="player-item"
        @mouseenter="showNextGame($event, player.teamCode)"
        @mouseleave="hideNextGame"
      >
        <FavoriteToggle :item="playerFavorite(player)" />
        <router-link class="entity-link entity-row-link" :to="`/player/${player.playerId}`" draggable="false" @click.stop>
          <div class="player-main">
            <TeamLogo :logoUrl="player.teamLogoUrl" :teamCode="player.teamCode" size="small" />
            <span class="player-name">{{ player.firstName }} {{ player.lastName }}</span>
          </div>
          <span class="player-icons">
            <span class="streak-count stat-key">Games: {{ player.currentPointStreak }}</span>
          </span>
        </router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { usePlayerStats } from '../composables/useApi'
import { useSearchStore } from '../stores/searchStore'
import { playerFavorite } from '../composables/useFavorites'
import TeamLogo from './TeamLogo.vue'
import { useNextGames } from '../composables/useNextGames'
import FavoriteToggle from './FavoriteToggle.vue'

const { showNextGame, hideNextGame } = useNextGames()
const { loading, error, getPlayerStreaks } = usePlayerStats()
const searchStore = useSearchStore()
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
  const data = await getPlayerStreaks()
  if (data) {
    allPlayers.value = data
  }
}

onMounted(() => {
  loadData()
})

</script>

<style scoped>
.player-list {
  width: 100%;
}

.players-grid {
  display: flex;
  flex-direction: column;
  gap: var(--row-gap);
  padding-right: 1.5rem;
}

.player-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--row-padding);
  background-color: var(--row-bg);
  border: var(--color-border-thick) solid var(--row-border);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
  position: relative;
}

.player-item:hover {
  background-color: var(--row-hover-bg);
  transform: translateX(var(--row-hover-shift));
  z-index: 10;
}

.player-main {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  flex: 1;
}

.player-name {
  color: var(--color-name);
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

.player-icons {
  display: flex;
  gap: 0.5rem;
  align-items: center;
}

.streak-count {
  color: var(--color-text-primary);
  font-size: 1rem;
  font-weight: bold;
  white-space: nowrap;
}


.loading,
.error,
.empty {
  text-align: center;
  padding: 2rem;
  color: var(--color-text-secondary);
}
</style>
