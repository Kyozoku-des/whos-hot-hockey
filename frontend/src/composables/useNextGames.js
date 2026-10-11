import { ref } from 'vue'
import { useApi } from './useApi'
import { timeZoneOption } from './useSettings'

// Shared across every table: one request feeds all rows, refreshed when older than this
const MAX_AGE_MS = 5 * 60 * 1000
const LIVE_STATES = ['LIVE', 'CRIT']

const nextGamesByTeam = ref({})
let loadedAt = 0
let pending = null

// Row currently hovered on a pointer device: { teamCode, rect }. The game is looked up from the
// cache when rendered, so a refresh while hovering updates the tooltip.
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
    // Timers are throttled in background tabs, so a hover also refreshes stale data
    load()
    if (!canHover()) return
    hovered.value = { teamCode, rect: event.currentTarget.getBoundingClientRect() }
  }

  const hideNextGame = () => {
    hovered.value = null
  }

  return { nextGameFor, showNextGame, hideNextGame, hovered, refresh: load }
}

// "vs BOS" at home, "@ BOS" away
export const formatOpponent = (game) => `${game.homeGame ? 'vs' : '@'} ${game.opponentTeamCode}`

// Calendar day of a date in the given time zone, for comparing days
const dayKey = (date, timeZone) => date.toLocaleDateString('en-CA', { timeZone })

// "Live now", "Today 7:00 PM", "Tomorrow 7:00 PM" or "Sat, Oct 11, 7:00 PM" in the
// time zone picked in settings (the viewer's own by default)
export const formatGameTime = (game) => {
  if (LIVE_STATES.includes(game.gameState)) return 'Live now'

  const start = game.startTimeUtc ? new Date(game.startTimeUtc) : null
  if (!start || Number.isNaN(start.getTime())) return game.gameDate

  const timeZone = timeZoneOption()
  const time = start.toLocaleTimeString([], { hour: 'numeric', minute: '2-digit', timeZone })
  const startDay = dayKey(start, timeZone)
  const today = dayKey(new Date(), timeZone)
  // Next calendar day after today's YYYY-MM-DD, counted in UTC so DST can't skew it
  const tomorrow = new Date(Date.parse(today) + 24 * 60 * 60 * 1000).toISOString().slice(0, 10)
  if (startDay === today) return `Today ${time}`
  if (startDay === tomorrow) return `Tomorrow ${time}`

  const day = start.toLocaleDateString([], { weekday: 'short', month: 'short', day: 'numeric', timeZone })
  return `${day}, ${time}`
}
