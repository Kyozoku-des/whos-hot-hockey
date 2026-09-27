import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

// The data-job re-syncs hourly, so a cached index older than this may be
// missing players or carrying a season that has since rolled over.
const CACHE_TTL_MS = 30 * 60 * 1000

export const useSearchStore = defineStore('search', () => {
  // State
  const searchData = ref([])
  const loadedSeason = ref(null) // season the cached index belongs to
  const isLoaded = ref(false)
  const lastUpdated = ref(null)
  const currentQuery = ref('') // Current search query for real-time filtering

  // Getters
  const isStale = computed(() => {
    if (!isLoaded.value || !lastUpdated.value) return true
    return Date.now() - lastUpdated.value.getTime() > CACHE_TTL_MS
  })

  // Actions

  /**
   * Load the search index. No-op when a fresh copy is already cached unless
   * `force` is set. `fetchFn` must resolve to a { season, count, results }
   * envelope; the season is what lets us notice a season rollover and drop
   * the previous season's index instead of serving it forever.
   */
  const loadSearchData = async (fetchFn, { force = false } = {}) => {
    if (isLoaded.value && !isStale.value && !force) {
      return true
    }

    try {
      const index = await fetchFn()
      if (!index) {
        return false
      }

      if (loadedSeason.value && index.season && loadedSeason.value !== index.season) {
        console.info(`Search index season changed ${loadedSeason.value} -> ${index.season}, replacing cache`)
      }

      searchData.value = index.results || []
      loadedSeason.value = index.season || null
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

    // Filter and score matches by name only
    const matches = searchData.value
      .map(item => {
        const name = item.name.toLowerCase()
        const matchIndex = name.indexOf(lowerQuery)

        if (matchIndex !== -1) {
          // Score: lower is better
          // Prioritize matches at the start of the name
          const score = matchIndex + (name.length - lowerQuery.length)
          return { ...item, score }
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
    loadedSeason.value = null
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
    loadedSeason,
    isLoaded,
    lastUpdated,
    currentQuery,
    // Getters
    isStale,
    // Actions
    loadSearchData,
    searchItems,
    clearCache,
    setQuery
  }
})
