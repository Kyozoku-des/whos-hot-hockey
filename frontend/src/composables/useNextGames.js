import { ref } from 'vue'
import { useApi } from './useApi'

// Shared across every table: one request feeds all rows, refreshed when older than this
const MAX_AGE_MS = 10 * 60 * 1000
const LIVE_STATES = ['LIVE', 'CRIT']

const nextGamesByTeam = ref({})
let loadedAt = 0
let pending = null

// Row currently hovered on a pointer device: { game, rect }
const hovered = ref(null)

const load = () => {
  if (pending || Date.now() - loadedAt < MAX_AGE_MS) return
  const { fetchData } = useApi()
  pending = fetchData('/teams/next-games')
    .then(data => {
      if (data) {
        nextGamesByTeam.value = Object.fromEntries(data.map(game => [game.teamCode, game]))
        loadedAt = Date.now()
      }
    })
    .finally(() => {
      pending = null
    })
}

const canHover = () => window.matchMedia?.('(hover: hover)').matches ?? false

export function useNextGames() {
  load()

  const nextGameFor = (teamCode) => nextGamesByTeam.value[teamCode] ?? null

  // Mouse hover only: on touch devices a tap opens the player or team page, which shows the next game
  const showNextGame = (event, teamCode) => {
    const game = nextGameFor(teamCode)
    if (!game || !canHover()) return
    hovered.value = { game, rect: event.currentTarget.getBoundingClientRect() }
  }

  const hideNextGame = () => {
    hovered.value = null
  }

  return { nextGameFor, showNextGame, hideNextGame, hovered }
}

// "vs BOS" at home, "@ BOS" away
export const formatOpponent = (game) => `${game.homeGame ? 'vs' : '@'} ${game.opponentTeamCode}`

// "Live now", "Today 7:00 PM", "Tomorrow 7:00 PM" or "Sat, Oct 11, 7:00 PM" in the viewer's time zone
export const formatGameTime = (game) => {
  if (LIVE_STATES.includes(game.gameState)) return 'Live now'

  const start = game.startTimeUtc ? new Date(game.startTimeUtc) : null
  if (!start || Number.isNaN(start.getTime())) return game.gameDate

  const time = start.toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })
  const today = new Date()
  const tomorrow = new Date(today)
  tomorrow.setDate(today.getDate() + 1)
  if (start.toDateString() === today.toDateString()) return `Today ${time}`
  if (start.toDateString() === tomorrow.toDateString()) return `Tomorrow ${time}`

  const day = start.toLocaleDateString([], { weekday: 'short', month: 'short', day: 'numeric' })
  return `${day}, ${time}`
}
