import { ref } from 'vue'

// Relative by default: nginx (prod) and the Vite dev server both proxy /api to
// the backend, so the bundle is not baked against a specific host. VITE_API_BASE_URL
// must be supplied at BUILD time (Vite inlines import.meta.env), not at runtime,
// and must already include the /api suffix.
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api'

export function useApi() {
  const loading = ref(false)
  const error = ref(null)

  const fetchData = async (endpoint) => {
    loading.value = true
    error.value = null

    try {
      const response = await fetch(`${API_BASE_URL}${endpoint}`)
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`)
      }
      const data = await response.json()
      return data
    } catch (e) {
      error.value = e.message
      console.error('API fetch error:', e)
      return null
    } finally {
      loading.value = false
    }
  }

  return {
    loading,
    error,
    fetchData
  }
}

// Specific API hooks for different data types
export function usePlayerStats() {
  const { loading, error, fetchData } = useApi()

  const getTopScorers = async () => {
    const data = await fetchData(`/players/standings`)
    return data || []
  }

  const getPlayerStreaks = async () => {
    const data = await fetchData(`/players/point-streaks`)
    return data || []
  }

  const getHottestPlayers = async () => {
    const data = await fetchData(`/players/hot`)
    return data || []
  }

  const getPlayerDetails = async (playerId) => {
    return await fetchData(`/players/${playerId}`)
  }

  const getPlayerGameLog = async (playerId, season = null) => {
    const endpoint = season
      ? `/players/${playerId}/game-log?season=${season}`
      : `/players/${playerId}/game-log`
    const data = await fetchData(endpoint)
    return data || []
  }

  return {
    loading,
    error,
    getTopScorers,
    getPlayerStreaks,
    getHottestPlayers,
    getPlayerDetails,
    getPlayerGameLog
  }
}

export function useTeamStats() {
  const { loading, error, fetchData } = useApi()

  const getStandings = async () => {
    const data = await fetchData(`/teams/standings`)
    return data || []
  }

  const getTeamWinStreaks = async () => {
    const data = await fetchData(`/teams/win-streaks`)
    return data || []
  }

  const getTeamLoseStreaks = async () => {
    const data = await fetchData(`/teams/loss-streaks`)
    return data || []
  }

  const getTeamDetails = async (teamCode) => {
    return await fetchData(`/teams/${teamCode}`)
  }

  const getTeamGameLog = async (teamCode, season = null) => {
    const endpoint = season
      ? `/teams/${teamCode}/game-log?season=${season}`
      : `/teams/${teamCode}/game-log`
    const data = await fetchData(endpoint)
    return data || []
  }

  return {
    loading,
    error,
    getStandings,
    getTeamWinStreaks,
    getTeamLoseStreaks,
    getTeamDetails,
    getTeamGameLog
  }
}

export function useGameScores() {
  const { loading, error, fetchData } = useApi()

  // Today's games while any is live, otherwise the latest day with finished games
  const getLatestScoreboard = async () => {
    const data = await fetchData(`/games/latest`)
    return data || { gameDate: null, live: false, games: [] }
  }

  return {
    loading,
    error,
    getLatestScoreboard
  }
}
