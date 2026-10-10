<template>
  <div class="player-page">
    <div class="container">
      <div v-if="loading" class="loading">Loading...</div>
      <div v-else-if="error" class="error">{{ error }}</div>
      <div v-else class="player-content">
        <div class="player-header">
          <div class="player-info-card">
            <PlayerAvatar
              class="card-image"
              :headshot-url="player?.headshotUrl"
              :alt="`${player?.firstName} ${player?.lastName}`"
              size="large"
            />
            <div class="player-stats">
              <h1 class="player-name">{{ player?.firstName }} {{ player?.lastName }}</h1>
              <p class="stat-line">Points: {{ player?.points || 0 }}</p>
              <p class="stat-line">Goals: {{ player?.goals || 0 }}</p>
              <p class="stat-line">Assists: {{ player?.assists || 0 }}</p>
              <NextGameLine :team-code="player?.teamCode" />
              <FavoriteButton v-if="player" :item="playerFavorite(player)" />
            </div>
          </div>
        </div>

        <div class="section">
          <PlayerGameLogGraph
            :current-season-data="gameLogs"
            :previous-season-data="previousSeasonGameLogs"
            :previous-season="previousSeason"
          />
        </div>

        <div class="section">
          <h2 class="section-title">Game Logs</h2>
          <div v-if="loadingGameLog" class="loading">Loading game logs...</div>
          <div v-else ref="tableScroll" class="table-scroll" :style="{ maxHeight: tableMaxHeight }">
            <table class="game-log-table">
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
                <tr v-for="game in gameLogsReversed" :key="game.gameId">
                  <td>{{ formatDate(game.gameDate) }}</td>
                  <td>{{ game.goals }}</td>
                  <td>{{ game.assists }}</td>
                  <td>{{ game.points }}</td>
                  <td>{{ game.timeOnIce }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <PlayerAvatar
          class="bottom-image"
          :headshot-url="player?.headshotUrl"
          :alt="`${player?.firstName} ${player?.lastName}`"
          size="large"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import { usePlayerStats } from '../composables/useApi'
import PlayerAvatar from '../components/PlayerAvatar.vue'
import PlayerGameLogGraph from '../components/PlayerGameLogGraph.vue'
import FavoriteButton from '../components/FavoriteButton.vue'
import NextGameLine from '../components/NextGameLine.vue'
import { playerFavorite } from '../composables/useFavorites'

const route = useRoute()
const { loading, error, getPlayerDetails, getPlayerGameLog } = usePlayerStats()

const player = ref(null)
const gameLogs = ref([])
const gameLogsReversed = ref([]) // For table display (most recent first)
const previousSeasonGameLogs = ref([])
const previousSeason = ref('')
const loadingGameLog = ref(false)
const tableScroll = ref(null)
const tableMaxHeight = ref('none')

// Show the latest VISIBLE_GAMES rows; the rest are reached by scrolling the table
const VISIBLE_GAMES = 10
const updateTableMaxHeight = () => {
  const rows = tableScroll.value?.querySelectorAll('tbody tr')
  if (!rows || rows.length <= VISIBLE_GAMES) {
    tableMaxHeight.value = 'none'
    return
  }
  tableMaxHeight.value = `${rows[VISIBLE_GAMES].offsetTop}px`
}

const formatDate = (dateString) => {
  if (!dateString) return 'N/A'
  const date = new Date(dateString)
  return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })
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
  const playerId = route.params.id

  const playerData = await getPlayerDetails(playerId)
  if (playerData) {
    player.value = playerData
  }

  loadingGameLog.value = true

  // Fetch current season game log
  const gameLogData = await getPlayerGameLog(playerId)
  if (gameLogData) {
    gameLogs.value = gameLogData
    // Reverse for table display (show most recent games first)
    gameLogsReversed.value = [...gameLogData].reverse()
  }

  // Fetch previous season game log
  const previousSeasonData = await getPlayerGameLog(playerId, previousSeason.value)
  if (previousSeasonData) {
    previousSeasonGameLogs.value = previousSeasonData
  }

  loadingGameLog.value = false
  await nextTick()
  updateTableMaxHeight()
  window.addEventListener('resize', updateTableMaxHeight)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', updateTableMaxHeight)
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
  /* minmax(0, 1fr) lets the column shrink to the screen instead of growing
     to fit the widest table or chart inside it. */
  grid-template-columns: minmax(0, 1fr);
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

.chart-placeholder {
  min-height: 200px;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: rgba(255, 255, 255, 0.1);
  border-radius: 4px;
}

.table-scroll {
  overflow: auto;
}

.game-log-table {
  width: 100%;
  border-collapse: collapse;
}

.game-log-table th {
  /* Header stays visible while the rows scroll */
  position: sticky;
  top: 0;
  background-color: var(--color-bg-dark);
  color: var(--color-text-secondary);
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
  .container {
    padding: 0 1rem;
  }

  .player-header {
    justify-content: center;
  }

  .player-info-card {
    flex: 1;
    flex-direction: column;
    text-align: center;
    padding: 1.5rem 1rem;
  }

  /* The image moves from the card to the bottom of the page */
  .player-info-card .card-image {
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

  .player-name {
    font-size: 1.5rem;
  }

  .section-title {
    font-size: 1.2rem;
  }

  .game-log-table {
    font-size: 0.8rem;
    letter-spacing: 1px;
  }

  .game-log-table th,
  .game-log-table td {
    padding: 0.5rem 0.35rem;
  }
}
</style>
