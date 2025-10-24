<template>
  <div class="team-list">
    <div v-if="loading" class="loading">Loading...</div>
    <div v-else-if="error" class="error">{{ error }}</div>
    <div v-else-if="teams.length === 0" class="empty">No teams found</div>
    <div v-else class="teams-grid">
      <div
        v-for="(team, index) in teams"
        :key="team.teamCode"
        class="team-item"
        @click="goToTeam(team.teamCode)"
      >
        <div class="team-main">
          <span class="team-rank">{{ index + 1 }}</span>
          <TeamLogo :logoUrl="team.logoUrl" :teamCode="team.teamCode" :alt="team.teamName" size="small" />
          <span class="team-name">{{ team.teamName }}</span>
          <span class="team-icons">
            <img v-if="team.hot" src="../assets/flame.png" alt="Hot" class="status-icon" title="Hot streak" />
            <img v-if="team.cold" src="../assets/snowflake.png" alt="Cold" class="status-icon" title="Cold streak" />
            <img v-if="team.pointStreak" src="../assets/graph.png" alt="Streak" class="status-icon" title="Point streak" />
          </span>
        </div>
        <span class="team-stats">
          <span class="stat-item">GP: {{ team.gamesPlayed }}</span>
          <span class="stat-item">W: {{ team.wins }}</span>
          <span class="stat-item">L: {{ team.losses }}</span>
          <span class="stat-item stat-points">PTS: {{ team.points }}</span>
          <span v-if="isExpanded" class="stat-item">OTL: {{ team.overtimeLosses }}</span>
          <span v-if="isExpanded" class="stat-item">P%: {{ (team.pointPercentage * 100).toFixed(1) }}%</span>
          <span v-if="isExpanded" class="stat-item">GF: {{ team.goalsFor }}</span>
          <span v-if="isExpanded" class="stat-item">GA: {{ team.goalsAgainst }}</span>
          <span v-if="isExpanded" class="stat-item" :class="{ 'stat-positive': team.goalDifferential > 0, 'stat-negative': team.goalDifferential < 0 }">
            DIFF: {{ team.goalDifferential > 0 ? '+' : '' }}{{ team.goalDifferential }}
          </span>
        </span>
        <div class="tooltip" v-if="team.nextOpponentCode">
          Next: {{ formatNextGame(team) }}
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, inject, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useTeamStats } from '../composables/useApi'
import TeamLogo from './TeamLogo.vue'

const router = useRouter()
const { loading, error, getStandings } = useTeamStats()
const teams = ref([])
const selectedSeason = inject('selectedSeason')
const isExpanded = inject('isExpanded', ref(false))

const loadData = async () => {
  const data = await getStandings(selectedSeason.value)
  if (data) {
    teams.value = data // Show all teams
  }
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

const formatNextGame = (team) => {
  if (!team.nextOpponentCode) return 'N/A'
  const location = team.nextGameIsHome ? 'vs' : '@'
  return `${location} ${team.nextOpponentCode}`
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

.stat-points {
  font-weight: bold;
}

.stat-positive {
  color: #4ade80;
}

.stat-negative {
  color: #f87171;
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
