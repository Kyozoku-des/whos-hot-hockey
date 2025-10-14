<template>
  <div class="card">
    <h2 class="card-title">Top 10 Points</h2>
    <div v-if="loading" class="loading">Loading...</div>
    <div v-else-if="error" class="error">{{ error }}</div>
    <table v-else class="stats-table">
      <thead>
        <tr>
          <th>Name</th>
          <th>G</th>
          <th>A</th>
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
          <td>{{ player.goals }}</td>
          <td>{{ player.assists }}</td>
          <td>{{ player.points }}</td>
          <td>{{ player.gamesPlayed }}</td>
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
const { loading, error, getTopScorers } = usePlayerStats()
const players = ref([])

onMounted(async () => {
  const data = await getTopScorers(10)
  if (data) {
    players.value = data
  }
})

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
