<template>
  <div class="player-page">
    <div class="container">
      <HomeLink class="page-home-link" />
      <div v-if="loading" class="loading">Loading...</div>
      <div v-else-if="error" class="error">{{ error }}</div>
      <div v-else class="player-content">
        <div class="player-header">
          <div class="page-title">
            <TeamLogo :logoUrl="player?.teamLogoUrl" :teamCode="player?.teamCode" size="medium" />
            <h1 class="player-name">{{ player?.firstName }} {{ player?.lastName }}</h1>
          </div>
          <NextGameLine :team-code="player?.teamCode" />
          <FavoriteButton v-if="player" :item="playerFavorite(player)" />
        </div>

        <div class="section">
          <PlayerGameLogGraph
            :current-season-data="gameLogs"
            :previous-season-data="previousSeasonGameLogs"
            :previous-season="previousSeason"
            :season-games="player?.seasonGames"
            :projected-points="player?.projectedPoints"
          />
        </div>

        <div class="section season-totals">
          <div class="total">
            <span class="total-label">Points</span>
            <span class="total-value">{{ player?.points || 0 }}</span>
          </div>
          <div class="total">
            <span class="total-label">Goals</span>
            <span class="total-value">{{ player?.goals || 0 }}</span>
          </div>
          <div class="total">
            <span class="total-label">Assists</span>
            <span class="total-value">{{ player?.assists || 0 }}</span>
          </div>
        </div>

        <div class="section">
          <h2 class="section-title">Game Logs</h2>
          <div v-if="loadingGameLog" class="loading">Loading game logs...</div>
          <div v-else ref="tableScroll" class="table-scroll themed-scroll" :style="{ maxHeight: tableMaxHeight }">
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
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { applySeo, entitySeo, unavailableEntitySeo } from '../seo.js'
import { useRoute } from 'vue-router'
import { usePlayerStats } from '../composables/useApi'
import PlayerGameLogGraph from '../components/PlayerGameLogGraph.vue'
import HomeLink from '../components/HomeLink.vue'
import FavoriteButton from '../components/FavoriteButton.vue'
import NextGameLine from '../components/NextGameLine.vue'
import TeamLogo from '../components/TeamLogo.vue'
import { playerFavorite } from '../composables/useFavorites'

const route = useRoute()
const pagePath = route.path
const { loading, error, status, getPlayerDetails, getPlayerGameLog } = usePlayerStats()

const player = ref(null)
const gameLogs = ref([])
const gameLogsReversed = ref([]) // For table display (most recent first)
const previousSeasonGameLogs = ref([])
const previousSeason = ref('')
const loadingGameLog = ref(false)
const tableScroll = ref(null)
const tableMaxHeight = ref('none')
// Re-measures when row heights change after the first paint (late web font,
// text wrapping on narrow screens, rotation), not only on window resize
let tableResizeObserver = null

// Show the latest VISIBLE_GAMES rows; the rest are reached by scrolling the table
const VISIBLE_GAMES = 5
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
  if (!playerData) {
    // Only a missing player is permanent; a rate limit or outage keeps the page indexable.
    if (route.path === pagePath && status.value === 404) applySeo(unavailableEntitySeo('player', pagePath))
    return
  }
  player.value = playerData
  if (route.path === pagePath) applySeo(entitySeo('player', pagePath, `${playerData.firstName} ${playerData.lastName}`))

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
  const table = tableScroll.value?.querySelector('table')
  if (table) {
    tableResizeObserver = new ResizeObserver(updateTableMaxHeight)
    tableResizeObserver.observe(table)
  }
})

onBeforeUnmount(() => {
  tableResizeObserver?.disconnect()
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

.page-home-link {
  display: inline-block;
  margin: 1.5rem 0 1rem;
}

.player-content {
  display: grid;
  /* minmax(0, 1fr) lets the column shrink to the screen instead of growing
     to fit the widest table or chart inside it. */
  grid-template-columns: minmax(0, 1fr);
  gap: 2rem;
}

/* Name, next game and favorite button on one line, wrapping on narrow screens */
.player-header {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.75rem 2rem;
  background-color: var(--color-bg-card);
  border-radius: 8px;
  padding: 1.5rem 2rem;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.page-title {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.player-name {
  font-size: 2rem;
  font-weight: 700;
  margin: 0;
}

.stat-line {
  font-size: 1.1rem;
  margin: 0;
}

/* Pushed to the far right of the header */
.player-header .favorite-button {
  margin-top: 0;
  margin-left: auto;
}

.season-totals {
  display: flex;
  justify-content: space-around;
  gap: 1rem;
}

.total {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.25rem;
}

.total-label {
  color: var(--color-text-secondary);
  font-weight: 700;
}

.total-value {
  font-size: 1.75rem;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
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
  border-bottom: 1px solid var(--color-line);
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
  color: var(--color-negative, #d32f2f);
}

@media (max-width: 768px) {
  .container {
    padding: 0 1rem;
  }

  .player-header {
    flex-direction: column;
    text-align: center;
    padding: 1.5rem 1rem;
  }

  .page-title {
    gap: 0.75rem;
  }

  .player-header .favorite-button {
    margin-left: 0;
  }

  .total-value {
    font-size: 1.35rem;
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
    letter-spacing: calc(1 * var(--tracking));
  }

  .game-log-table th,
  .game-log-table td {
    padding: 0.5rem 0.35rem;
  }
}
</style>
