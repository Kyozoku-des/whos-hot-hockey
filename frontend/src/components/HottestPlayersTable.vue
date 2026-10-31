<template>
  <div class="player-list stat-table" style="--stat-cols: 4">
    <div v-if="loading" class="loading">Loading...</div>
    <div v-else-if="error" class="error">{{ error }}</div>
    <div v-else-if="players.length === 0" class="empty">No hot players</div>
    <div v-else class="players-grid">
      <div
        v-for="player in shownPlayers"
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
            <AbsentBadge :player="player" />
            <NextGameBadge :team-code="player.teamCode" />
          </div>
          <span class="player-stats">
            <span class="stat-item" title="Points over the last 10 games">P: {{ player.pointsInLastNGames ?? 0 }}</span>
            <span class="stat-item stat-key" title="Points per game over the last 10 games">PPG: {{ player.pointsPerLastNGames?.toFixed(2) ?? '0.00' }}</span>
            <span class="stat-item" title="Season points if the last-10 points per game holds for the team's games left">Pace: {{ player.projectedPointsLastN ?? '–' }}</span>
            <!-- Only while the last 10 still covers fewer games: from then on it is always 10 -->
            <span v-if="player.lastNGamesPlayed < LAST_N_GAMES" class="stat-item" title="Games played, all of which the last-10 figures cover">GP: {{ player.lastNGamesPlayed }}</span>
          </span>
        </router-link>
      </div>
    </div>
    <div v-if="hasMore" ref="sentinel" class="load-more" aria-hidden="true"></div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { usePlayerStats } from '../composables/useApi'
import { useSearchStore } from '../stores/searchStore'
import { playerFavorite } from '../composables/useFavorites'
import TeamLogo from './TeamLogo.vue'
import NextGameBadge from './NextGameBadge.vue'
import { useNextGames } from '../composables/useNextGames'
import FavoriteToggle from './FavoriteToggle.vue'
import { useIncrementalList } from '../composables/useIncrementalList'
import { matchesPosition } from '../composables/positions'
import AbsentBadge from './AbsentBadge.vue'

// Games the last-10 figures cover once the player has played that many
const LAST_N_GAMES = 10

const props = defineProps({
  // Position group picked in the card title: 'all', 'F' or 'D'
  position: { type: String, default: 'all' }
})

const { showNextGame, hideNextGame } = useNextGames()
const { loading, error, getHottestPlayers } = usePlayerStats()
const searchStore = useSearchStore()
const allPlayers = ref([])

// Filter players based on the position picked and the search query
const players = computed(() => {
  const inPosition = allPlayers.value.filter(player => matchesPosition(player, props.position))
  const query = searchStore.currentQuery.toLowerCase().trim()
  if (!query) return inPosition

  // Find teams matching the query
  const matchingTeamCodes = searchStore.searchData
    .filter(item => item.type === 'TEAM' && item.name.toLowerCase().includes(query))
    .map(team => team.id)

  return inPosition.filter(player => {
    // Check if player name matches
    const fullName = `${player.firstName} ${player.lastName}`.toLowerCase()
    if (fullName.includes(query)) return true

    // Check if player's team matches
    if (player.teamCode && matchingTeamCodes.includes(player.teamCode)) return true

    return false
  })
})

// Rows are added as the list is scrolled
const { visible: shownPlayers, hasMore, sentinel } = useIncrementalList(players)

const loadData = async () => {
  const data = await getHottestPlayers()
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

.player-stats {
  display: flex;
  gap: 1rem;
  align-items: center;
}

.stat-item {
  color: var(--color-stat);
  font-size: 0.9rem;
  font-weight: normal;
  white-space: nowrap;
}


.loading,
.error,
.empty {
  text-align: center;
  padding: 2rem;
  color: var(--color-text-secondary);
}

/* Watched to add more rows as the list nears its end */
.load-more {
  height: 1px;
}
</style>
