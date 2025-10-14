<template>
  <div class="card">
    <h2 class="card-title">Hottest Players</h2>
    <div class="controls">
      <label>
        PPG threshold
        <input
          v-model.number="threshold"
          type="number"
          step="0.1"
          min="0"
          class="control-input"
          @change="fetchData"
        />
      </label>
      <label>
        for last
        <input
          v-model.number="games"
          type="number"
          min="1"
          class="control-input"
          @change="fetchData"
        />
        games
      </label>
    </div>
    <div v-if="loading" class="loading">Loading...</div>
    <div v-else-if="error" class="error">{{ error }}</div>
    <table v-else class="stats-table">
      <thead>
        <tr>
          <th>Name</th>
          <th>PPG</th>
          <th>P</th>
          <th>GP</th>
        </tr>
      </thead>
      <tbody>
        <tr
          v-for="player in players"
          :key="player.playerId"
          @click="goToPlayer(player.playerId)"
          class="clickable-row"
        >
          <td>{{ player.firstName }} {{ player.lastName }}</td>
          <td>{{ player.pointsPerGame.toFixed(2) }}</td>
          <td>{{ player.recentPoints }}</td>
          <td>{{ player.recentGamesPlayed }}</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { usePlayerStats } from '../composables/useApi'

const router = useRouter()
const { loading, error, getHottestPlayers } = usePlayerStats()
const players = ref([])
const threshold = ref(1.5)
const games = ref(5)

const fetchData = async () => {
  const data = await getHottestPlayers(games.value, 20)
  if (data) {
    // Filter by threshold on frontend
    players.value = data.filter(p => p.pointsPerGame >= threshold.value)
  }
}

onMounted(fetchData)

const goToPlayer = (playerId) => {
  router.push(`/player/${playerId}`)
}
</script>

<style scoped>
.card {
  background-color: var(--color-bg-card);
  border-radius: 8px;
  padding: 1.5rem;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.card-title {
  font-size: 1.5rem;
  font-weight: 700;
  margin-bottom: 1rem;
  text-align: center;
}

.controls {
  display: flex;
  justify-content: center;
  gap: 1rem;
  margin-bottom: 1rem;
  flex-wrap: wrap;
}

.controls label {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.95rem;
}

.control-input {
  padding: 0.25rem 0.5rem;
  border: 1px solid var(--color-border);
  border-radius: 4px;
  width: 60px;
  font-size: 0.9rem;
}

.stats-table {
  width: 100%;
  border-collapse: collapse;
}

.stats-table thead {
  background-color: var(--color-bg-dark);
  color: var(--color-text-secondary);
}

.stats-table th {
  padding: 0.75rem;
  text-align: left;
  font-weight: 700;
}

.stats-table tbody tr {
  border-bottom: 1px solid var(--color-border);
}

.stats-table td {
  padding: 0.75rem;
}

.clickable-row {
  cursor: pointer;
  transition: background-color 0.2s;
}

.clickable-row:hover {
  background-color: rgba(255, 255, 255, 0.1);
}

.loading,
.error {
  text-align: center;
  padding: 2rem;
}

.error {
  color: #d32f2f;
}
</style>
