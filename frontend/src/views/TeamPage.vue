<template>
  <div class="team-page">
    <div class="container">
      <div v-if="loading" class="loading">Loading...</div>
      <div v-else-if="error" class="error">{{ error }}</div>
      <div v-else class="team-content">
        <div class="team-header">
          <div class="team-info-card">
            <TeamLogo
              class="card-image"
              :logoUrl="team?.logoUrl"
              :teamCode="team?.teamCode"
              :alt="team?.teamName"
              size="large"
            />
            <div class="team-stats">
              <h1 class="team-name">{{ team?.teamName }}</h1>
              <p class="stat-line">Wins: {{ team?.wins || 0 }}</p>
              <p class="stat-line">Losses: {{ team?.losses || 0 }}</p>
              <p class="stat-line">Points: {{ team?.points || 0 }}</p>
              <NextGameLine :team-code="team?.teamCode" />
              <FavoriteButton v-if="team" :item="teamFavorite(team)" />
            </div>
          </div>
        </div>

        <div class="section">
          <TeamGameLogGraph
            :current-season-data="teamGameLogs"
            :previous-season-data="previousSeasonTeamGameLogs"
            :previous-season="previousSeason"
            :season-games="team?.seasonGames"
            :projected-points="team?.projectedPoints"
          />
        </div>

        <div class="section">
          <h2 class="section-title">Roster</h2>
          <ul v-if="sortedRoster.length" class="roster-list">
            <li class="roster-row roster-header">
              <span class="roster-name">Player</span>
              <span class="roster-stats">
                <span class="roster-stat">G</span>
                <span class="roster-stat">A</span>
                <span class="roster-stat">P</span>
              </span>
            </li>
            <li
              v-for="player in sortedRoster"
              :key="player.playerId"
              class="roster-row roster-player"
              @click="goToPlayer(player.playerId)"
            >
              <span class="roster-name">{{ player.fullName }}</span>
              <span class="roster-stats">
                <span class="roster-stat">{{ player.goals ?? 0 }}</span>
                <span class="roster-stat">{{ player.assists ?? 0 }}</span>
                <span class="roster-stat roster-points">{{ player.points ?? 0 }}</span>
              </span>
            </li>
          </ul>
          <p v-else class="placeholder-text">No roster data available</p>
        </div>

        <TeamLogo
          class="bottom-image"
          :logoUrl="team?.logoUrl"
          :teamCode="team?.teamCode"
          :alt="team?.teamName"
          size="large"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useTeamStats } from '../composables/useApi'
import TeamLogo from '../components/TeamLogo.vue'
import TeamGameLogGraph from '../components/TeamGameLogGraph.vue'
import FavoriteButton from '../components/FavoriteButton.vue'
import NextGameLine from '../components/NextGameLine.vue'
import { teamFavorite } from '../composables/useFavorites'

const route = useRoute()
const { loading, error, getTeamDetails, getTeamGameLog } = useTeamStats()

const team = ref(null)
const teamGameLogs = ref([])
const previousSeasonTeamGameLogs = ref([])
const previousSeason = ref('')

const router = useRouter()

// Highest scorers first
const sortedRoster = computed(() =>
  [...(team.value?.roster || [])].sort((a, b) => (b.points ?? 0) - (a.points ?? 0))
)

const goToPlayer = (playerId) => {
  router.push(`/player/${playerId}`)
}

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
  previousSeason.value = calculatePreviousSeason()
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
  const previousSeasonData = await getTeamGameLog(teamId, previousSeason.value)
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
  /* minmax(0, 1fr) lets the column shrink to the screen instead of growing
     to fit the widest table or chart inside it. */
  grid-template-columns: minmax(0, 1fr);
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

.bottom-image {
  display: none;
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

.roster-list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.roster-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 1rem;
  padding: 0.75rem;
}

/* Matches the game log table on the player page */
.roster-header {
  font-weight: 700;
  background-color: var(--color-bg-dark);
  color: var(--color-text-secondary);
}

.roster-player {
  border-bottom: 1px solid var(--color-border);
  cursor: pointer;
}

.roster-player:hover {
  background-color: rgba(255, 255, 255, 0.1);
}

.roster-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.roster-stats {
  display: flex;
  flex-shrink: 0;
}

.roster-stat {
  width: 3rem;
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.roster-points {
  font-weight: 700;
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
  .container {
    padding: 0 1rem;
  }

  .team-header {
    justify-content: center;
  }

  .team-info-card {
    flex: 1;
    flex-direction: column;
    text-align: center;
    padding: 1.5rem 1rem;
  }

  /* The image moves from the card to the bottom of the page */
  .team-info-card .card-image {
    display: none;
  }

  .bottom-image {
    display: block;
    margin: 0 auto;
  }

  .section {
    padding: 1rem 0.75rem;
  }

  .section :deep(.game-log-graph) {
    padding: 0.5rem 0;
  }

  .team-name {
    font-size: 1.5rem;
  }

  .section-title {
    font-size: 1.2rem;
  }

  .roster-row {
    font-size: 0.8rem;
    letter-spacing: 1px;
    padding: 0.5rem 0.35rem;
  }
}
</style>
