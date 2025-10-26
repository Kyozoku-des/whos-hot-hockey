import { ref, computed } from 'vue'

const STORAGE_KEY = 'nhl_favorites'
const CONSENT_KEY = 'nhl_consent'
const MAX_FAVORITES = 10

// Reactive favorites list (shared across components)
const favorites = ref([])
const consentGiven = ref(null)

export function useFavorites() {
  // Initialize consent status and load favorites
  const initializeFavorites = () => {
    checkConsent()
    if (consentGiven.value !== null) {
      loadFavorites()
    }
  }

  // Check if user has given consent
  const checkConsent = () => {
    try {
      const consent = localStorage.getItem(CONSENT_KEY)
      if (consent) {
        const parsed = JSON.parse(consent)
        consentGiven.value = parsed.given
        return parsed.given
      }

      // Check sessionStorage for declined consent
      const sessionConsent = sessionStorage.getItem(CONSENT_KEY)
      if (sessionConsent) {
        const parsed = JSON.parse(sessionConsent)
        consentGiven.value = parsed.given
        return parsed.given
      }

      return null
    } catch (error) {
      console.error('Error checking consent:', error)
      return null
    }
  }

  // Accept consent
  const acceptConsent = () => {
    try {
      const consent = {
        given: true,
        timestamp: new Date().toISOString()
      }
      localStorage.setItem(CONSENT_KEY, JSON.stringify(consent))
      consentGiven.value = true

      // Migrate any session favorites to localStorage
      const sessionFavorites = sessionStorage.getItem(STORAGE_KEY)
      if (sessionFavorites) {
        localStorage.setItem(STORAGE_KEY, sessionFavorites)
        sessionStorage.removeItem(STORAGE_KEY)
      }

      loadFavorites()
    } catch (error) {
      console.error('Error accepting consent:', error)
    }
  }

  // Decline consent
  const declineConsent = () => {
    try {
      const consent = {
        given: false,
        timestamp: new Date().toISOString()
      }
      sessionStorage.setItem(CONSENT_KEY, JSON.stringify(consent))
      consentGiven.value = false

      // Clear any localStorage favorites
      localStorage.removeItem(STORAGE_KEY)
      loadFavorites()
    } catch (error) {
      console.error('Error declining consent:', error)
    }
  }

  // Get storage based on consent
  const getStorage = () => {
    if (consentGiven.value === true) {
      return localStorage
    } else if (consentGiven.value === false) {
      return sessionStorage
    }
    return null
  }

  // Load favorites from storage
  const loadFavorites = () => {
    try {
      const storage = getStorage()
      if (!storage) {
        favorites.value = []
        return
      }

      const stored = storage.getItem(STORAGE_KEY)
      if (stored) {
        favorites.value = JSON.parse(stored)
      } else {
        favorites.value = []
      }
    } catch (error) {
      console.error('Error loading favorites:', error)
      favorites.value = []
    }
  }

  // Save favorites to storage
  const saveFavorites = () => {
    try {
      const storage = getStorage()
      if (!storage) {
        console.warn('Cannot save favorites: no consent given')
        return false
      }

      storage.setItem(STORAGE_KEY, JSON.stringify(favorites.value))
      return true
    } catch (error) {
      console.error('Error saving favorites:', error)
      return false
    }
  }

  // Add a favorite
  const addFavorite = (item) => {
    if (!canAddMore()) {
      return { success: false, error: 'Maximum 10 favorites reached' }
    }

    if (isFavorited(item.id)) {
      return { success: false, error: 'Already favorited' }
    }

    const favorite = {
      id: item.id,
      type: item.type,
      name: item.name,
      imageUrl: item.imageUrl || '',
      secondaryInfo: item.secondaryInfo || '',
      addedAt: new Date().toISOString()
    }

    favorites.value.push(favorite)
    saveFavorites()
    return { success: true }
  }

  // Remove a favorite
  const removeFavorite = (id) => {
    const index = favorites.value.findIndex(fav => fav.id === id)
    if (index !== -1) {
      favorites.value.splice(index, 1)
      saveFavorites()
      return { success: true }
    }
    return { success: false, error: 'Favorite not found' }
  }

  // Check if item is favorited
  const isFavorited = (id) => {
    return favorites.value.some(fav => fav.id === id)
  }

  // Toggle favorite status
  const toggleFavorite = (item) => {
    if (isFavorited(item.id)) {
      return removeFavorite(item.id)
    } else {
      return addFavorite(item)
    }
  }

  // Get all favorites
  const getFavorites = () => {
    return favorites.value
  }

  // Get favorites count
  const getFavoritesCount = () => {
    return favorites.value.length
  }

  // Check if can add more favorites
  const canAddMore = () => {
    return favorites.value.length < MAX_FAVORITES
  }

  // Clear all favorites
  const clearAll = () => {
    favorites.value = []
    saveFavorites()
  }

  // Computed property for favorites count
  const favoritesCount = computed(() => favorites.value.length)

  // Computed property for can add more
  const canAdd = computed(() => favorites.value.length < MAX_FAVORITES)

  // Check if consent is needed
  const needsConsent = computed(() => consentGiven.value === null)

  return {
    // State
    favorites,
    consentGiven,
    favoritesCount,
    canAdd,
    needsConsent,

    // Methods
    initializeFavorites,
    checkConsent,
    acceptConsent,
    declineConsent,
    addFavorite,
    removeFavorite,
    isFavorited,
    toggleFavorite,
    getFavorites,
    getFavoritesCount,
    canAddMore,
    clearAll
  }
}
