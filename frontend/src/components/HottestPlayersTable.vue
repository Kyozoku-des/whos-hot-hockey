<template>
  <div class="player-list">
    <div v-if="loading" class="loading">Loading...</div>
    <div v-else-if="error" class="error">{{ error }}</div>
    <div v-else-if="players.length === 0" class="empty">No hot players</div>
    <div v-else class="players-grid">
      <div
        v-for="player in players"
        :key="player.playerId"
        class="player-item"
        @click="goToPlayer(player.playerId)"
      >
        <span class="player-name">{{ player.firstName }} {{ player.lastName }}</span>
        <span class="player-stats">
          <span class="stat-item">P: {{ player.points }}</span>
          <span class="stat-item">GP: {{ player.gamesPlayed }}</span>
          <span class="stat-item">PPG: {{ player.pointsPerGame?.toFixed(2) || '0.00' }}</span>
          <img src="../assets/flame.png" alt="Hot" class="status-icon" title="Hot player" />
        </span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, inject, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { usePlayerStats } from '../composables/useApi'

const router = useRouter()
const { loading, error, getHottestPlayers } = usePlayerStats()
const players = ref([])
const selectedSeason = inject('selectedSeason')

const loadData = async () => {
  const data = await getHottestPlayers(5, 20, selectedSeason.value)
  if (data) {
    players.value = data
  }
}

onMounted(() => {
  loadData()
})

watch(selectedSeason, () => {
  loadData()
})

const goToPlayer = (playerId) => {
  router.push(`/player/${playerId}`)
}
</script>

<style scoped>
.player-list {
  width: 100%;
}

.players-grid {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  padding-right: 1.5rem;
}

.player-item {
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

.player-item:hover {
  background-color: rgba(255, 255, 255, 0.1);
  transform: translateX(5px);
  z-index: 10;
}

.player-name {
  color: var(--color-text-secondary);
  font-size: 1rem;
  font-weight: bold;
  flex: 1;
}

.player-stats {
  display: flex;
  gap: 1rem;
  align-items: center;
}

.stat-item {
  color: var(--color-text-primary);
  font-size: 0.9rem;
  font-weight: normal;
  white-space: nowrap;
}

.status-icon {
  width: 20px;
  height: 20px;
  object-fit: contain;
  margin-left: 0.25rem;
}

.loading,
.error,
.empty {
  text-align: center;
  padding: 2rem;
  color: var(--color-text-secondary);
}
</style>
