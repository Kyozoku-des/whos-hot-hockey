<template>
  <div class="card">
    <h2 class="card-title">Active Point Streaks</h2>
    <div class="search-box">
      <input
        v-model="searchQuery"
        type="text"
        placeholder="search player"
        class="search-input"
      />
    </div>
    <div v-if="loading" class="loading">Loading...</div>
    <div v-else-if="error" class="error">{{ error }}</div>
    <table v-else class="stats-table">
      <thead>
        <tr>
          <th>Name</th>
          <th>PSG</th>
        </tr>
      </thead>
      <tbody>
        <tr
          v-for="player in filteredPlayers"
          :key="player.playerId"
          @click="goToPlayer(player.playerId)"
          class="clickable-row"
        >
          <td>{{ player.firstName }} {{ player.lastName }}</td>
          <td>{{ player.streakLength }}</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { usePlayerStats } from '../composables/useApi'

const router = useRouter()
const { loading, error, getPlayerStreaks } = usePlayerStats()
const players = ref([])
const searchQuery = ref('')

onMounted(async () => {
  const data = await getPlayerStreaks()
  if (data) {
    players.value = data
  }
})

const filteredPlayers = computed(() => {
  if (!searchQuery.value) {
    return players.value
  }
  const query = searchQuery.value.toLowerCase()
  return players.value.filter(player => {
    const fullName = `${player.firstName} ${player.lastName}`.toLowerCase()
    return fullName.includes(query)
  })
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

.search-box {
  margin-bottom: 1rem;
  text-align: center;
}

.search-input {
  padding: 0.5rem 1rem;
  border: 1px solid var(--color-border);
  border-radius: 4px;
  width: 100%;
  max-width: 300px;
  font-size: 0.95rem;
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
