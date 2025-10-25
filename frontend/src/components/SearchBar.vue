<template>
  <div class="search-bar" ref="searchBarRef">
    <div class="search-input-wrapper">
      <input
        type="text"
        v-model="searchQuery"
        @input="handleInput"
        @keydown="handleKeydown"
        @focus="isFocused = true"
        placeholder="Search players or teams..."
        class="search-input"
      />
      <span v-if="isSearching" class="search-icon">🔍</span>
    </div>

    <div v-if="showDropdown" class="search-dropdown">
      <div v-if="!searchStore.isLoaded" class="dropdown-loading">
        Loading search data...
      </div>
      <div v-else-if="searchResults.length === 0" class="dropdown-empty">
        No results found
      </div>
      <div
        v-else
        v-for="(result, index) in searchResults"
        :key="`${result.type}-${result.id}`"
        :class="['dropdown-item', { 'dropdown-item-selected': index === selectedIndex }]"
        @click="selectResult(result)"
        @mouseenter="selectedIndex = index"
      >
        <div class="item-image">
          <img
            v-if="result.imageUrl"
            :src="result.imageUrl"
            :alt="result.name"
            @error="handleImageError"
          />
          <div v-else class="image-placeholder">
            {{ result.type === 'PLAYER' ? '👤' : '🏒' }}
          </div>
        </div>
        <div class="item-info">
          <div class="item-name">{{ result.name }}</div>
          <div class="item-secondary">{{ result.secondaryInfo }}</div>
        </div>
        <div class="item-badge" :class="`badge-${result.type.toLowerCase()}`">
          {{ result.type }}
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useSearchStore } from '../stores/searchStore'
import { useApi } from '../composables/useApi'

const router = useRouter()
const searchStore = useSearchStore()
const { fetchData } = useApi()

// State
const searchQuery = ref('')
const searchResults = ref([])
const selectedIndex = ref(0)
const isFocused = ref(false)
const isSearching = ref(false)
const searchBarRef = ref(null)
let debounceTimeout = null

// Computed
const showDropdown = computed(() => {
  return isFocused.value && searchQuery.value.trim() !== ''
})

// Methods
const handleInput = () => {
  isSearching.value = true

  // Update the current query immediately for real-time table filtering
  searchStore.setQuery(searchQuery.value)

  // Clear previous timeout
  if (debounceTimeout) {
    clearTimeout(debounceTimeout)
  }

  // Debounce for 1 second (for dropdown results)
  debounceTimeout = setTimeout(() => {
    performSearch()
    isSearching.value = false
  }, 1000)
}

const performSearch = () => {
  if (!searchQuery.value.trim()) {
    searchResults.value = []
    return
  }

  searchResults.value = searchStore.searchItems(searchQuery.value)
  selectedIndex.value = 0
}

const handleKeydown = (event) => {
  if (!showDropdown.value) return

  switch (event.key) {
    case 'ArrowDown':
      event.preventDefault()
      selectedIndex.value = Math.min(selectedIndex.value + 1, searchResults.value.length - 1)
      break
    case 'ArrowUp':
      event.preventDefault()
      selectedIndex.value = Math.max(selectedIndex.value - 1, 0)
      break
    case 'Enter':
      event.preventDefault()
      if (searchResults.value[selectedIndex.value]) {
        selectResult(searchResults.value[selectedIndex.value])
      }
      break
    case 'Escape':
      event.preventDefault()
      closeDropdown()
      break
  }
}

const selectResult = (result) => {
  const path = result.type === 'PLAYER'
    ? `/player/${result.id}`
    : `/team/${result.id}`

  router.push(path)
  closeDropdown()
}

const closeDropdown = () => {
  isFocused.value = false
  searchQuery.value = ''
  searchResults.value = []
  selectedIndex.value = 0
  searchStore.setQuery('') // Clear the filter query
}

const handleClickOutside = (event) => {
  if (searchBarRef.value && !searchBarRef.value.contains(event.target)) {
    closeDropdown()
  }
}

const handleImageError = (event) => {
  event.target.style.display = 'none'
}

// Load search data on mount
onMounted(async () => {
  if (!searchStore.isLoaded) {
    await searchStore.loadSearchData(async () => {
      return await fetchData('/search/all')
    })
  }

  // Add click outside listener
  document.addEventListener('click', handleClickOutside)
})

onUnmounted(() => {
  // Clean up
  if (debounceTimeout) {
    clearTimeout(debounceTimeout)
  }
  document.removeEventListener('click', handleClickOutside)
})
</script>

<style scoped>
.search-bar {
  position: relative;
  width: 100%;
  max-width: 500px;
}

.search-input-wrapper {
  position: relative;
  width: 100%;
}

.search-input {
  width: 100%;
  padding: 0.75rem 2.5rem 0.75rem 1rem;
  font-size: 1rem;
  font-family: var(--font-family);
  color: var(--color-text-primary);
  background-color: var(--color-bg-card);
  border: 2px solid var(--color-border);
  border-radius: 8px;
  outline: none;
  transition: all 0.2s ease;
}

.search-input:focus {
  border-color: var(--color-text-secondary);
  box-shadow: 0 0 0 3px rgba(255, 170, 0, 0.1);
}

.search-input::placeholder {
  color: rgba(255, 255, 255, 0.4);
}

.search-icon {
  position: absolute;
  right: 1rem;
  top: 50%;
  transform: translateY(-50%);
  font-size: 1.2rem;
  opacity: 0.5;
}

.search-dropdown {
  position: absolute;
  top: calc(100% + 0.5rem);
  left: 0;
  right: 0;
  background-color: var(--color-bg-card);
  border: 2px solid var(--color-border);
  border-radius: 8px;
  max-height: 400px;
  overflow-y: auto;
  z-index: 1000;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
}

.dropdown-loading,
.dropdown-empty {
  padding: 1.5rem;
  text-align: center;
  color: var(--color-text-secondary);
  font-style: italic;
}

.dropdown-item {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 0.75rem 1rem;
  cursor: pointer;
  transition: background-color 0.2s ease;
  border-bottom: 1px solid var(--color-border);
}

.dropdown-item:last-child {
  border-bottom: none;
}

.dropdown-item:hover,
.dropdown-item-selected {
  background-color: rgba(255, 170, 0, 0.1);
}

.item-image {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  overflow: hidden;
  flex-shrink: 0;
  background-color: rgba(255, 255, 255, 0.05);
  display: flex;
  align-items: center;
  justify-content: center;
}

.item-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.image-placeholder {
  font-size: 1.5rem;
}

.item-info {
  flex: 1;
  min-width: 0;
}

.item-name {
  font-weight: 600;
  color: var(--color-text-primary);
  font-size: 1rem;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.item-secondary {
  font-size: 0.85rem;
  color: var(--color-text-secondary);
  margin-top: 0.15rem;
}

.item-badge {
  padding: 0.25rem 0.5rem;
  border-radius: 4px;
  font-size: 0.75rem;
  font-weight: 600;
  text-transform: uppercase;
  flex-shrink: 0;
}

.badge-player {
  background-color: rgba(59, 130, 246, 0.2);
  color: #60a5fa;
}

.badge-team {
  background-color: rgba(239, 68, 68, 0.2);
  color: #f87171;
}

/* Scrollbar styling */
.search-dropdown::-webkit-scrollbar {
  width: 8px;
}

.search-dropdown::-webkit-scrollbar-track {
  background: rgba(255, 255, 255, 0.05);
  border-radius: 4px;
}

.search-dropdown::-webkit-scrollbar-thumb {
  background: rgba(255, 170, 0, 0.3);
  border-radius: 4px;
}

.search-dropdown::-webkit-scrollbar-thumb:hover {
  background: rgba(255, 170, 0, 0.5);
}

@media (max-width: 768px) {
  .search-bar {
    max-width: 100%;
  }

  .search-dropdown {
    max-height: 300px;
  }

  .item-badge {
    display: none;
  }
}
</style>
