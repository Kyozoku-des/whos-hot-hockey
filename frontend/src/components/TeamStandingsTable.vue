<template>
  <div class="team-list">
    <div v-if="loading" class="loading">Loading...</div>
    <div v-else-if="error" class="error">{{ error }}</div>
    <div v-else-if="teams.length === 0" class="empty">No teams found</div>
    <div v-else class="teams-grid">
      <div
        v-for="team in teams"
        :key="team.teamCode"
        class="team-item"
        @click="goToTeam(team.teamCode)"
      >
        <span class="team-name">{{ team.teamName }}</span>
        <span class="team-icons">
          <img v-if="team.hot" src="../assets/flame.png" alt="Hot" class="status-icon" title="Hot streak (3+ wins)" />
          <img v-if="team.cold" src="../assets/snowflake.png" alt="Cold" class="status-icon" title="Cold streak (3+ losses)" />
          <img v-if="team.pointStreak" src="../assets/graph.png" alt="Streak" class="status-icon" title="Win streak (5+ games)" />
        </span>
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

const loadData = async () => {
  const data = await getStandings(selectedSeason.value)
  if (data) {
    teams.value = data.slice(0, 5) // Top 5 teams
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
</script>

<style scoped>
.team-list {
  width: 100%;
}

.teams-grid {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
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
}

.team-item:hover {
  background-color: rgba(255, 255, 255, 0.1);
  transform: translateX(5px);
}

.team-name {
  color: var(--color-text-secondary);
  font-size: 1rem;
  font-weight: bold;
}

.team-icons {
  display: flex;
  gap: 0.5rem;
}

.status-icon {
  width: 20px;
  height: 20px;
  object-fit: contain;
}

.loading,
.error,
.empty {
  text-align: center;
  padding: 2rem;
  color: var(--color-text-secondary);
}
</style>
