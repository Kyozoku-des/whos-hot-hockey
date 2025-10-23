<template>
  <div class="team-list">
    <div v-if="loading" class="loading">Loading...</div>
    <div v-else-if="error" class="error">{{ error }}</div>
    <div v-else-if="teams.length === 0" class="empty">No hot teams</div>
    <div v-else class="teams-grid">
      <div
        v-for="(team, index) in teams"
        :key="team.teamCode"
        class="team-item"
        @click="goToTeam(team.teamCode)"
      >
        <div class="team-main">
          <span class="team-rank">{{ index + 1 }}</span>
          <span class="team-name">{{ team.teamName }}</span>
          <span class="team-icons">
            <img src="../assets/flame.png" alt="Hot" class="status-icon" title="Hot team" />
          </span>
        </div>
        <span class="team-stats">
          <span class="stat-item">W: {{ team.wins }}</span>
          <span class="stat-item">L: {{ team.losses }}</span>
          <span class="stat-item">OTL: {{ team.overtimeLosses }}</span>
          <span v-if="isExpanded" class="stat-item">GP: {{ team.gamesPlayed }}</span>
          <span v-if="isExpanded" class="stat-item">PTS: {{ team.points }}</span>
          <span v-if="isExpanded" class="stat-item">Streak: {{ team.currentWinStreak }}</span>
        </span>
        <div class="tooltip">
          {{ formatWinPercentage(team.last10GamesWinPercentage) }} W/GP
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, inject, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useTeamStats } from '../composables/useApi'

const router = useRouter()
const { loading, error, getStandings } = useTeamStats()
const teams = ref([])
const selectedSeason = inject('selectedSeason')
const isExpanded = inject('isExpanded', ref(false))

const loadData = async () => {
  const data = await getStandings(selectedSeason.value)
  if (data) {
    // Sort all teams by last 10 games win percentage (descending)
    teams.value = data
      .filter(team => team.last10GamesWinPercentage != null)
      .sort((a, b) => b.last10GamesWinPercentage - a.last10GamesWinPercentage)
  }
}

const formatWinPercentage = (percentage) => {
  if (percentage == null) return 'N/A'
  return percentage.toFixed(2)
}

onMounted(() => {
  loadData()
})

watch(selectedSeason, () => {
  loadData()
})

const goToTeam = (teamCode) => {
  router.push(`/team/${teamCode}`)
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
  padding-top: 2.5rem;
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

.loading,
.error,
.empty {
  text-align: center;
  padding: 2rem;
  color: var(--color-text-secondary);
}
</style>
