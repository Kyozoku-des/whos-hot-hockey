<template>
  <div class="home-page">
    <!-- Desktop header -->
    <div class="header desktop-header">
      <h1 class="title">WHOS HOT NHL</h1>
      <div class="search-container">
        <SearchBar />
      </div>
      <CurrentSeasonDisplay />
    </div>

    <!-- Mobile header: title above search bar, both centered -->
    <div class="header mobile-header">
      <h1 class="title mobile-title">WHOS HOT NHL</h1>
      <div class="search-container">
        <SearchBar />
      </div>
    </div>

    <!-- Latest game day's scores and point scorers -->
    <ScoreTicker />

    <!-- Desktop: grid of cards, rearranged by dragging one onto another -->
    <div class="content-container desktop-content">
      <div class="cards-grid">
        <div
          v-for="card in desktopCards"
          :key="card.key"
          class="card-slot"
          :class="{
            dragging: draggedKey === card.key,
            'drop-target': dropKey === card.key && draggedKey !== card.key
          }"
          draggable="true"
          @dragstart="onDragStart($event, card.key)"
          @dragover.prevent="onDragOver(card.key)"
          @drop.prevent="onDrop(card.key)"
          @dragend="onDragEnd"
        >
          <ExpandableCard :title="card.title" :class="{ 'favorites-card': card.key === 'favorites' }">
            <component :is="card.component" />
          </ExpandableCard>
        </div>
      </div>
    </div>

    <!-- Mobile: swipeable cards (one at a time) -->
    <div class="content-container mobile-content">
      <div
        class="swipe-container"
        ref="swipeContainer"
        @touchstart="onTouchStart"
        @touchmove="onTouchMove"
        @touchend="onTouchEnd"
      >
        <div class="swipe-track" :class="{ dragging: isSwiping }" :style="{ left: `${swipeOffset}px` }">
          <div v-for="(card, index) in mobileCards" :key="card.key" class="swipe-slide">
            <ExpandableCard :title="card.title" :class="{ 'favorites-card': card.key === 'favorites' }">
              <component :is="card.component" />
            </ExpandableCard>
          </div>
        </div>
      </div>
      <div class="swipe-dots">
        <span
          v-for="(card, index) in mobileCards"
          :key="card.key"
          :class="['dot', { active: index === activeCardIndex }]"
          @click="goToCard(index)"
        ></span>
      </div>
    </div>

    <!-- Cookie Consent Banner -->
    <CookieConsent />
  </div>
</template>

<script setup>
import { computed, onMounted, ref, onUnmounted, nextTick, watch, shallowRef } from 'vue'
import ExpandableCard from '../components/ExpandableCard.vue'
import TopPointsTable from '../components/TopPointsTable.vue'
import PointStreaksTable from '../components/PointStreaksTable.vue'
import HottestPlayersTable from '../components/HottestPlayersTable.vue'
import TeamStandingsTable from '../components/TeamStandingsTable.vue'
import TeamWinStreaksTable from '../components/TeamWinStreaksTable.vue'
import TeamHotTable from '../components/TeamHotTable.vue'
import CurrentSeasonDisplay from '../components/CurrentSeasonDisplay.vue'
import SearchBar from '../components/SearchBar.vue'
import ScoreTicker from '../components/ScoreTicker.vue'
import FavoritesTable from '../components/FavoritesTable.vue'
import CookieConsent from '../components/CookieConsent.vue'
import { useFavorites } from '../composables/useFavorites'

const { initializeFavorites, favoritesCount, consentGiven } = useFavorites()

// Show favorites card only if user has favorites
const showFavorites = computed(() => favoritesCount.value > 0)

// Mobile swipe state
const activeCardIndex = ref(0)
const swipeContainer = ref(null)
const touchStartX = ref(0)
const touchCurrentX = ref(0)
const isSwiping = ref(false)
const slideWidth = ref(0)

const CARDS = [
  { key: 'favorites', title: 'My Favorites', component: FavoritesTable },
  { key: 'standings', title: 'Player standings', component: TopPointsTable },
  { key: 'streaks', title: 'Point streaks', component: PointStreaksTable },
  { key: 'hot-players', title: 'Last 10 games', component: HottestPlayersTable },
  { key: 'team-standings', title: 'Team standings', component: TeamStandingsTable },
  { key: 'win-streaks', title: 'Win streaks', component: TeamWinStreaksTable },
  { key: 'team-hot', title: 'Last 10 games', component: TeamHotTable }
]

// The favorites card only shows once something has been favorited
const visibleCards = (cards) =>
  cards.filter(card => card.key !== 'favorites' || showFavorites.value)

const mobileCards = computed(() => visibleCards(CARDS))

// Desktop card order, changed by drag and drop. Saved alongside favorites,
// so it is only remembered between visits when storage consent was given.
const CARD_ORDER_KEY = 'nhl_card_order'
const cardOrder = ref(CARDS.map(card => card.key))

const desktopCards = computed(() =>
  visibleCards(cardOrder.value.map(key => CARDS.find(card => card.key === key)))
)

const loadCardOrder = () => {
  if (consentGiven.value !== true) return
  try {
    const saved = JSON.parse(localStorage.getItem(CARD_ORDER_KEY) || '[]')
    const known = saved.filter(key => CARDS.some(card => card.key === key))
    // Cards added since the order was saved go at the end
    const missing = CARDS.map(card => card.key).filter(key => !known.includes(key))
    cardOrder.value = [...known, ...missing]
  } catch {
    // Keep the default order
  }
}

const saveCardOrder = () => {
  if (consentGiven.value !== true) return
  try {
    localStorage.setItem(CARD_ORDER_KEY, JSON.stringify(cardOrder.value))
  } catch {
    // Order still applies for this visit
  }
}

const draggedKey = ref(null)
const dropKey = ref(null)

const onDragStart = (event, key) => {
  // An expanded card is an overlay, not something to rearrange
  if (event.currentTarget.querySelector('.expandable-card.expanded')) {
    event.preventDefault()
    return
  }
  draggedKey.value = key
  event.dataTransfer.effectAllowed = 'move'
  // Drag the whole card, even when the drag starts on a logo image
  event.dataTransfer.setDragImage(event.currentTarget, event.offsetX, event.offsetY)
}

const onDragOver = (key) => {
  if (draggedKey.value) dropKey.value = key
}

// Dropping a card on another swaps their places
const onDrop = (key) => {
  const from = cardOrder.value.indexOf(draggedKey.value)
  const to = cardOrder.value.indexOf(key)
  if (from !== -1 && to !== -1 && from !== to) {
    const order = [...cardOrder.value]
    ;[order[from], order[to]] = [order[to], order[from]]
    cardOrder.value = order
    saveCardOrder()
  }
  onDragEnd()
}

const onDragEnd = () => {
  draggedKey.value = null
  dropKey.value = null
}

const swipeOffset = computed(() => {
  const base = -(activeCardIndex.value * slideWidth.value)
  if (isSwiping.value) {
    return base + (touchCurrentX.value - touchStartX.value)
  }
  return base
})

const updateSlideWidth = () => {
  if (swipeContainer.value) {
    slideWidth.value = swipeContainer.value.offsetWidth
  }
}

// A touch only becomes a swipe once it has moved this far, and only if it moved
// more sideways than up/down; otherwise it is left to scroll the table.
const SWIPE_LOCK_DISTANCE = 10
let touchStartY = 0
let tracking = false

const onTouchStart = (e) => {
  // An expanded card is an overlay; scrolling inside it must not change slides.
  if (e.target.closest('.expandable-card.expanded, .backdrop')) return
  touchStartX.value = e.touches[0].clientX
  touchCurrentX.value = e.touches[0].clientX
  touchStartY = e.touches[0].clientY
  tracking = true
}

const onTouchMove = (e) => {
  if (!tracking) return
  const x = e.touches[0].clientX
  if (!isSwiping.value) {
    const dx = Math.abs(x - touchStartX.value)
    const dy = Math.abs(e.touches[0].clientY - touchStartY)
    if (Math.max(dx, dy) < SWIPE_LOCK_DISTANCE) return
    if (dy >= dx) {
      tracking = false
      return
    }
    isSwiping.value = true
  }
  touchCurrentX.value = x
}

const onTouchEnd = () => {
  tracking = false
  if (!isSwiping.value) return
  isSwiping.value = false
  const diff = touchCurrentX.value - touchStartX.value
  const threshold = slideWidth.value * 0.2

  if (diff < -threshold && activeCardIndex.value < mobileCards.value.length - 1) {
    activeCardIndex.value++
  } else if (diff > threshold && activeCardIndex.value > 0) {
    activeCardIndex.value--
  }
}

const goToCard = (index) => {
  activeCardIndex.value = index
}

// Initialize favorites on mount
onMounted(() => {
  initializeFavorites()
  loadCardOrder()
  nextTick(updateSlideWidth)
  window.addEventListener('resize', updateSlideWidth)
})

onUnmounted(() => {
  window.removeEventListener('resize', updateSlideWidth)
})
</script>

<style scoped>
.home-page {
  width: 100%;
  min-height: 100vh;
  background-color: var(--color-bg-primary);
}

/* Header base styles */
.header {
  border-bottom: var(--color-border-thick) solid var(--color-border);
  background-color: var(--color-bg-card);
}

.desktop-header {
  display: flex;
  align-items: center;
  padding: 0rem 1rem;
  gap: 2rem;
}

.mobile-header {
  display: none;
  flex-direction: column;
  align-items: center;
  padding: 1rem 1.5rem;
  gap: 1rem;
}

.title {
  font-size: 2rem;
  font-weight: bold;
  color: var(--color-text-primary);
  letter-spacing: 3px;
  text-transform: uppercase;
  margin: 0;
  padding-right: 3rem;
  border-right: var(--color-border-thick) solid var(--color-border);
  flex-shrink: 0;
}

.mobile-title {
  font-size: 1.75rem;
  padding-right: 0;
  border-right: none;
}

.search-container {
  flex: 1;
  max-width: 500px;
}

.desktop-header :deep(.current-season-display) {
  margin-left: auto;
}

.content-container {
  padding: 5rem 2rem 2rem 2rem;
}

.card-slot {
  min-width: 0;
  height: 100%;
  border-radius: 12px;
  transition: opacity 0.15s ease;
}

.card-slot.dragging {
  opacity: 0.4;
}

.card-slot.drop-target {
  outline: 3px dashed var(--color-text-secondary);
  outline-offset: 4px;
}

.favorites-card {
  border: 2px solid #FFAA00;
  box-shadow: 0 4px 12px rgba(255, 170, 0, 0.2);
}

.cards-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  grid-auto-rows: 1fr;
  gap: 1.5rem;
  max-width: 1600px;
  margin: 0 auto;
  min-height: 700px;
}

/* Mobile content hidden on desktop */
.mobile-content {
  display: none;
}

/* Swipe carousel styles */
.swipe-container {
  overflow: hidden;
  width: 100%;
  position: relative;
  /* The browser handles vertical scrolling; horizontal moves are our swipes. */
  touch-action: pan-y;
}

/* Moved with relative `left`, not transform: a transformed ancestor becomes
   the containing block for position: fixed, which would trap an expanded card
   inside the track. (A negative margin-left would widen the track and stretch
   the slides instead of moving them.) */
.swipe-track {
  display: flex;
  position: relative;
  left: 0;
  transition: left 0.3s ease;
}

.swipe-track.dragging {
  transition: none;
}

/* Fixed at exactly one container width: if a slide grew to fit its rows, the
   swipe offset (index * container width) would no longer line up with it. */
.swipe-slide {
  flex: 0 0 100%;
  min-width: 0;
  padding: 0 0.5rem;
  box-sizing: border-box;
}

.swipe-slide :deep(.expandable-card) {
  height: auto;
}

.swipe-slide :deep(.expandable-card.expanded) {
  height: 85vh;
}

.swipe-dots {
  display: flex;
  justify-content: center;
  gap: 0.5rem;
  padding: 1rem 0;
}

.dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background-color: rgba(255, 255, 255, 0.3);
  cursor: pointer;
  transition: background-color 0.2s ease;
}

.dot.active {
  background-color: var(--color-text-secondary);
}

@media (max-width: 1200px) {
  .cards-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 1024px) {
  .desktop-header {
    flex-wrap: wrap;
  }

  .search-container {
    order: 3;
    flex-basis: 100%;
    max-width: 100%;
  }
}

@media (max-width: 768px) {
  /* Switch to mobile layout */
  .desktop-header {
    display: none;
  }

  .mobile-header {
    display: flex;
  }

  .mobile-header .search-container {
    width: 100%;
    max-width: 100%;
  }

  .desktop-content {
    display: none;
  }

  .mobile-content {
    display: block;
    padding: 1rem 0.5rem;
  }

  /* Table rows: name on the first line, stats wrapped onto a second line so a
     row never needs more than the phone's width. The extra .mobile-content
     class outranks the table components' own scoped rules. */
  .mobile-content .swipe-slide :deep(.expandable-card) {
    padding: 0.75rem;
  }

  /* No hover on touch screens, so the card's hover-to-scroll never kicks in:
     make the list scrollable outright, without the page scrolling along. */
  .mobile-content .swipe-slide :deep(.card-content) {
    overflow-y: auto;
    overscroll-behavior: contain;
  }

  .mobile-content .swipe-slide :deep(.players-grid),
  .mobile-content .swipe-slide :deep(.teams-grid),
  .mobile-content .swipe-slide :deep(.favorites-grid) {
    padding-right: 0;
  }

  .mobile-content .swipe-slide :deep(.player-item),
  .mobile-content .swipe-slide :deep(.team-item) {
    flex-wrap: wrap;
    row-gap: 0.35rem;
    padding: 0.6rem 0.75rem;
  }

  .mobile-content .swipe-slide :deep(.player-item:hover),
  .mobile-content .swipe-slide :deep(.team-item:hover) {
    transform: none;
  }

  .mobile-content .swipe-slide :deep(.player-main),
  .mobile-content .swipe-slide :deep(.team-main) {
    min-width: 0;
    /* Full first line, so the stats always wrap below the name */
    flex-basis: 100%;
  }

  .mobile-content .swipe-slide :deep(.player-name),
  .mobile-content .swipe-slide :deep(.team-name) {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .mobile-content .swipe-slide :deep(.player-stats),
  .mobile-content .swipe-slide :deep(.team-stats) {
    flex-basis: 100%;
    flex-wrap: wrap;
    gap: 0.25rem 0.75rem;
  }

  /* Room for the remove button on the left of favorites rows */
  .mobile-content .swipe-slide :deep(.favorites-grid .player-main),
  .mobile-content .swipe-slide :deep(.favorites-grid .team-main),
  .mobile-content .swipe-slide :deep(.favorites-grid .player-stats),
  .mobile-content .swipe-slide :deep(.favorites-grid .team-stats) {
    padding-left: 2rem;
  }

  .mobile-content .swipe-slide :deep(.stat-item) {
    font-size: 0.8rem;
  }
}
</style>
