import { ref } from 'vue'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api'

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

  const getTopScorers = async (limit = 10) => {
    const data = await fetchData(`/players/standings?limit=${limit}`)
    return data || []
  }

  const getPlayerStreaks = async (minGames = 3) => {
    const data = await fetchData(`/players/point-streaks?minGames=${minGames}`)
    return data || []
  }

  const getHottestPlayers = async (games = 5, limit = 20) => {
    const data = await fetchData(`/players/hot?games=${games}&limit=${limit}`)
    return data || []
  }

  const getPlayerDetails = async (playerId) => {
    return await fetchData(`/players/${playerId}`)
  }

  const getPlayerGameLog = async (playerId) => {
    const data = await fetchData(`/players/${playerId}`)
    return data?.recentGames || []
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
    const data = await fetchData('/standings')
    return data || []
  }

  const getTeamWinStreaks = async (minGames = 2) => {
    const data = await fetchData(`/teams/win-streaks?minGames=${minGames}`)
    return data || []
  }

  const getTeamLoseStreaks = async (minGames = 2) => {
    const data = await fetchData(`/teams/losing-streaks?minGames=${minGames}`)
    return data || []
  }

  const getTeamDetails = async (teamCode) => {
    return await fetchData(`/teams/${teamCode}`)
  }

  return {
    loading,
    error,
    getStandings,
    getTeamWinStreaks,
    getTeamLoseStreaks,
    getTeamDetails
  }
}
