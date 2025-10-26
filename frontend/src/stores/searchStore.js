import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useSearchStore = defineStore('search', () => {
  // State
  const searchData = ref([])
  const isLoaded = ref(false)
  const lastUpdated = ref(null)
  const currentQuery = ref('') // Current search query for real-time filtering

  // Actions
  const loadSearchData = async (fetchFn) => {
    try {
      const data = await fetchFn()
      searchData.value = data || []
      isLoaded.value = true
      lastUpdated.value = new Date()
      return true
    } catch (error) {
      console.error('Error loading search data:', error)
      return false
    }
  }

  const searchItems = (query) => {
    if (!query || query.trim() === '') {
      return []
    }

    const lowerQuery = query.toLowerCase().trim()

    // First, find all matching teams
    const matchingTeams = searchData.value
      .filter(item => item.type === 'TEAM' && item.name.toLowerCase().includes(lowerQuery))

    // Get team codes from matching teams
    const matchingTeamCodes = matchingTeams.map(team => team.id)

    // Filter and score matches
    const matches = searchData.value
      .map(item => {
        const name = item.name.toLowerCase()
        const matchIndex = name.indexOf(lowerQuery)

        // Direct name match
        if (matchIndex !== -1) {
          // Score: lower is better
          // Prioritize matches at the start of the name
          const score = matchIndex + (name.length - lowerQuery.length)
          return { ...item, score, matchType: 'name' }
        }

        // If this is a player and their team matches the query
        if (item.type === 'PLAYER' && item.teamCode && matchingTeamCodes.includes(item.teamCode)) {
          // Give player team matches a higher score (lower priority than direct name matches)
          return { ...item, score: 1000, matchType: 'team' }
        }

        return null
      })
      .filter(item => item !== null)
      .sort((a, b) => a.score - b.score)
      .slice(0, 5) // Return max 5 results

    return matches
  }

  const clearCache = () => {
    searchData.value = []
    isLoaded.value = false
    lastUpdated.value = null
    currentQuery.value = ''
  }

  const setQuery = (query) => {
    currentQuery.value = query
  }

  return {
    // State
    searchData,
    isLoaded,
    lastUpdated,
    currentQuery,
    // Actions
    loadSearchData,
    searchItems,
    clearCache,
    setQuery
  }
})
