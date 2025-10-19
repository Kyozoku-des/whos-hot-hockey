<template>
  <div class="player-list">
    <div v-if="loading" class="loading">Loading...</div>
    <div v-else-if="error" class="error">{{ error }}</div>
    <div v-else-if="players.length === 0" class="empty">No point streaks</div>
    <div v-else class="players-grid">
      <div
        v-for="player in players"
        :key="player.playerId"
        class="player-item"
        @click="goToPlayer(player.playerId)"
      >
        <span class="player-name">{{ player.firstName }} {{ player.lastName }}</span>
        <span class="player-icons">
          <img v-if="player.hot" src="../assets/flame.png" alt="Hot" class="status-icon" title="Hot streak (PPG > 1.5)" />
          <img v-if="player.cold" src="../assets/snowflake.png" alt="Cold" class="status-icon" title="Cold streak (PPG < 0.2)" />
          <img v-if="player.pointStreak" src="../assets/graph.png" alt="Point Streak" class="status-icon" title="5+ game point streak" />
          <span class="streak-count">{{ player.currentPointStreak }}</span>
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
const { loading, error, getPlayerStreaks } = usePlayerStats()
const players = ref([])
const selectedSeason = inject('selectedSeason')

const loadData = async () => {
  const data = await getPlayerStreaks(3, selectedSeason.value)
  if (data) {
    players.value = data.slice(0, 5)
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
}

.player-item:hover {
  background-color: rgba(255, 255, 255, 0.1);
  transform: translateX(5px);
}

.player-name {
  color: var(--color-text-secondary);
  font-size: 1rem;
  font-weight: bold;
}

.player-icons {
  display: flex;
  gap: 0.5rem;
  align-items: center;
}

.status-icon {
  width: 20px;
  height: 20px;
  object-fit: contain;
}

.streak-count {
  color: var(--color-text-primary);
  font-size: 1.2rem;
  font-weight: bold;
  min-width: 30px;
  text-align: center;
}

.loading,
.error,
.empty {
  text-align: center;
  padding: 2rem;
  color: var(--color-text-secondary);
}
</style>
