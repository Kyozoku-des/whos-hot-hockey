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
        @mouseenter="showNextGame($event, team.teamCode)"
        @mouseleave="hideNextGame"
      >
        <FavoriteToggle :item="teamFavorite(team)" />
        <router-link class="entity-link entity-row-link" :to="`/team/${team.teamCode}`" draggable="false" @click.stop>
          <div class="team-main">
            <TeamLogo :logoUrl="team.logoUrl" :teamCode="team.teamCode" :alt="team.teamName" size="small" />
            <span class="team-name">{{ team.teamName }}</span>
            <NextGameBadge :team-code="team.teamCode" />
          </div>
          <span class="team-stats">
            <span class="stat-item stat-streak stat-key">Games: {{ team.currentWinStreak }}</span>
            <span v-if="isExpanded" class="stat-item">W: {{ team.wins }}</span>
            <span v-if="isExpanded" class="stat-item">L: {{ team.losses }}</span>
            <span v-if="isExpanded" class="stat-item">GP: {{ team.gamesPlayed }}</span>
            <span v-if="isExpanded" class="stat-item">PTS: {{ team.points }}</span>
          </span>
        </router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, inject, onMounted } from 'vue'
import { useTeamStats } from '../composables/useApi'
import { useSearchStore } from '../stores/searchStore'
import { teamFavorite } from '../composables/useFavorites'
import TeamLogo from './TeamLogo.vue'
import NextGameBadge from './NextGameBadge.vue'
import { useNextGames } from '../composables/useNextGames'
import FavoriteToggle from './FavoriteToggle.vue'

const { showNextGame, hideNextGame } = useNextGames()
const { loading, error, getStandings } = useTeamStats()
const searchStore = useSearchStore()
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

</script>

<style scoped>
.team-list {
  width: 100%;
}

.teams-grid {
  display: flex;
  flex-direction: column;
  gap: var(--row-gap);
  padding-right: 1.5rem;
}

.team-item {
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

.team-item:hover {
  background-color: var(--row-hover-bg);
  transform: translateX(var(--row-hover-shift));
  z-index: 10;
}

.team-main {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  flex: 1;
}

.team-rank {
  color: var(--color-text-primary);
  font-size: 1rem;
  font-weight: bold;
  min-width: 24px;
  text-align: center;
}

.team-name {
  color: var(--color-name);
  font-size: 1rem;
  font-weight: bold;
  flex: 1;
}


.team-stats {
  display: flex;
  gap: 1rem;
  align-items: center;
  flex-wrap: wrap;
}

.stat-item {
  color: var(--color-stat);
  font-size: 0.9rem;
  font-weight: normal;
  white-space: nowrap;
}

.stat-streak {
  font-weight: bold;
  font-size: 1rem;
}

.loading,
.error,
.empty {
  text-align: center;
  padding: 2rem;
  color: var(--color-text-secondary);
}
</style>
