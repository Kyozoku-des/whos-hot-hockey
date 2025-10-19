<template>
  <div class="player-page">
    <div class="container">
      <div v-if="loading" class="loading">Loading...</div>
      <div v-else-if="error" class="error">{{ error }}</div>
      <div v-else class="player-content">
        <div class="player-header">
          <div class="player-info-card">
            <PlayerAvatar
              :headshot-url="player?.headshotUrl"
              :alt="`${player?.firstName} ${player?.lastName}`"
              size="large"
            />
            <div class="player-stats">
              <h1 class="player-name">{{ player?.firstName }} {{ player?.lastName }}</h1>
              <p class="stat-line">Points: {{ player?.currentSeason?.points || 0 }}</p>
              <p class="stat-line">Goals: {{ player?.currentSeason?.goals || 0 }}</p>
              <p class="stat-line">Assists: {{ player?.currentSeason?.assists || 0 }}</p>
            </div>
          </div>
        </div>

        <div class="section">
          <h2 class="section-title">Points Progression</h2>
          <div class="chart-placeholder">
            <p>Chart visualization would go here</p>
          </div>
        </div>

        <div class="section">
          <h2 class="section-title">Game Logs</h2>
          <div v-if="loadingGameLog" class="loading">Loading game logs...</div>
          <table v-else class="game-log-table">
            <thead>
              <tr>
                <th>Date</th>
                <th>Goals</th>
                <th>Assists</th>
                <th>Points</th>
                <th>Time On Ice</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="game in gameLogs" :key="game.gameId">
                <td>{{ formatDate(game.date) }}</td>
                <td>{{ game.goals }}</td>
                <td>{{ game.assists }}</td>
                <td>{{ game.points }}</td>
                <td>{{ game.timeOnIce }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { usePlayerStats } from '../composables/useApi'
import PlayerAvatar from '../components/PlayerAvatar.vue'

const route = useRoute()
const { loading, error, getPlayerDetails, getPlayerGameLog } = usePlayerStats()

const player = ref(null)
const gameLogs = ref([])
const loadingGameLog = ref(false)

const formatDate = (dateString) => {
  if (!dateString) return 'N/A'
  const date = new Date(dateString)
  return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })
}

onMounted(async () => {
  const playerId = route.params.id

  const playerData = await getPlayerDetails(playerId)
  if (playerData) {
    player.value = playerData
  }

  loadingGameLog.value = true
  const gameLogData = await getPlayerGameLog(playerId)
  if (gameLogData) {
    gameLogs.value = gameLogData
  }
  loadingGameLog.value = false
})
</script>

<style scoped>
.player-page {
  width: 100%;
}

.container {
  max-width: 1400px;
  margin: 0 auto;
  padding: 0 2rem;
}

.player-content {
  display: grid;
  gap: 2rem;
}

.player-header {
  display: flex;
  gap: 2rem;
}

.player-info-card {
  background-color: var(--color-bg-card);
  border-radius: 8px;
  padding: 2rem;
  display: flex;
  gap: 2rem;
  align-items: center;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}


.player-stats {
  flex: 1;
}

.player-name {
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

.chart-placeholder {
  min-height: 200px;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: rgba(255, 255, 255, 0.1);
  border-radius: 4px;
}

.game-log-table {
  width: 100%;
  border-collapse: collapse;
}

.game-log-table thead {
  background-color: var(--color-bg-dark);
  color: var(--color-text-secondary);
}

.game-log-table th {
  padding: 0.75rem;
  text-align: left;
  font-weight: 700;
}

.game-log-table tbody tr {
  border-bottom: 1px solid var(--color-border);
}

.game-log-table td {
  padding: 0.75rem;
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
  .player-info-card {
    flex-direction: column;
    text-align: center;
  }
}
</style>
