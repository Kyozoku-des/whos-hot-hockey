<template>
  <div class="team-page">
    <div class="container">
      <div v-if="loading" class="loading">Loading...</div>
      <div v-else-if="error" class="error">{{ error }}</div>
      <div v-else class="team-content">
        <div class="team-header">
          <div class="team-info-card">
            <TeamLogo
              :logoUrl="team?.logoUrl"
              :teamCode="team?.teamCode"
              :alt="team?.teamName"
              size="large"
            />
            <div class="team-stats">
              <h1 class="team-name">{{ team?.teamName }}</h1>
              <p class="stat-line">Wins: {{ team?.currentSeason?.wins || 0 }}</p>
              <p class="stat-line">Losses: {{ team?.currentSeason?.losses || 0 }}</p>
              <p class="stat-line">Points: {{ team?.currentSeason?.points || 0 }}</p>
            </div>
          </div>
        </div>

        <div class="section">
          <h2 class="section-title">Team Statistics</h2>
          <div class="stats-grid">
            <div class="stat-item">
              <span class="stat-label">Goals Per Game:</span>
              <span class="stat-value">{{ team?.currentSeason?.goalsPerGame?.toFixed(2) || '0.00' }}</span>
            </div>
            <div class="stat-item">
              <span class="stat-label">Goals Against Per Game:</span>
              <span class="stat-value">{{ team?.currentSeason?.goalsAgainstPerGame?.toFixed(2) || '0.00' }}</span>
            </div>
            <div class="stat-item">
              <span class="stat-label">Power Play %:</span>
              <span class="stat-value">{{ team?.currentSeason?.powerPlayPercentage?.toFixed(1) || '0.0' }}%</span>
            </div>
            <div class="stat-item">
              <span class="stat-label">Penalty Kill %:</span>
              <span class="stat-value">{{ team?.currentSeason?.penaltyKillPercentage?.toFixed(1) || '0.0' }}%</span>
            </div>
          </div>
        </div>

        <div class="section">
          <h2 class="section-title">Win/Loss Progression</h2>
          <TeamGameLogGraph
            :current-season-data="teamGameLogs"
            :previous-season-data="previousSeasonTeamGameLogs"
          />
        </div>

        <div class="section">
          <h2 class="section-title">Roster</h2>
          <p class="placeholder-text">Team roster would be displayed here</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useTeamStats } from '../composables/useApi'
import TeamLogo from '../components/TeamLogo.vue'
import TeamGameLogGraph from '../components/TeamGameLogGraph.vue'

const route = useRoute()
const { loading, error, getTeamDetails, getTeamGameLog } = useTeamStats()

const team = ref(null)
const teamGameLogs = ref([])
const previousSeasonTeamGameLogs = ref([])

// Calculate previous season ID
const calculatePreviousSeason = () => {
  const currentYear = new Date().getFullYear()
  const currentMonth = new Date().getMonth() + 1

  let seasonStartYear
  if (currentMonth >= 10) {
    seasonStartYear = currentYear
  } else {
    seasonStartYear = currentYear - 1
  }

  const previousStartYear = seasonStartYear - 1
  const previousEndYear = seasonStartYear

  return `${previousStartYear}${previousEndYear}`
}

onMounted(async () => {
  const teamId = route.params.id

  const teamData = await getTeamDetails(teamId)
  if (teamData) {
    team.value = teamData
  }

  // Fetch current season game log
  const gameLogData = await getTeamGameLog(teamId)
  if (gameLogData) {
    teamGameLogs.value = gameLogData
  }

  // Fetch previous season game log
  const previousSeason = calculatePreviousSeason()
  const previousSeasonData = await getTeamGameLog(teamId, previousSeason)
  if (previousSeasonData) {
    previousSeasonTeamGameLogs.value = previousSeasonData
  }
})
</script>

<style scoped>
.team-page {
  width: 100%;
}

.container {
  max-width: 1400px;
  margin: 0 auto;
  padding: 0 2rem;
}

.team-content {
  display: grid;
  gap: 2rem;
}

.team-header {
  display: flex;
  gap: 2rem;
}

.team-info-card {
  background-color: var(--color-bg-card);
  border-radius: 8px;
  padding: 2rem;
  display: flex;
  gap: 2rem;
  align-items: center;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}


.team-stats {
  flex: 1;
}

.team-name {
  font-size: 2rem;
  font-weight: 700;
  margin-bottom: 1rem;
}

.stat-line {
  font-size: 1.1rem;
  margin: 0.5rem 0;
}

.section {
  background-color: var(--color-bg-card);
  border-radius: 8px;
  padding: 1.5rem;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.section-title {
  font-size: 1.5rem;
  font-weight: 700;
  margin-bottom: 1rem;
  text-align: center;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 1rem;
}

.stat-item {
  display: flex;
  justify-content: space-between;
  padding: 1rem;
  background-color: rgba(255, 255, 255, 0.1);
  border-radius: 4px;
}

.stat-label {
  font-weight: 600;
}

.stat-value {
  font-weight: 700;
  font-size: 1.2rem;
}

.placeholder-text {
  text-align: center;
  padding: 2rem;
  color: rgba(0, 0, 0, 0.6);
}

.loading,
.error {
  text-align: center;
  padding: 3rem;
  font-size: 1.2rem;
}

.error {
  color: #d32f2f;
}

@media (max-width: 768px) {
  .team-info-card {
    flex-direction: column;
    text-align: center;
  }

  .stats-grid {
    grid-template-columns: 1fr;
  }
}
</style>
