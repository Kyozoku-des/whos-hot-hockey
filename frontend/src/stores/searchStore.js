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

    // Filter and score matches
    const matches = searchData.value
      .map(item => {
        const name = item.name.toLowerCase()
        const matchIndex = name.indexOf(lowerQuery)

        if (matchIndex === -1) {
          return null
        }

        // Score: lower is better
        // Prioritize matches at the start of the name
        const score = matchIndex + (name.length - lowerQuery.length)

        return { ...item, score }
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
