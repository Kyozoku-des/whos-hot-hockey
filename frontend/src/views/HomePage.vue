<template>
  <div ref="homePage" class="home-page">
    <!-- Only one layout is mounted: each card fetches and renders its own
         rows, so a hidden second copy would double the work on every phone -->
    <!-- Desktop header -->
    <div v-if="!isMobile" class="header desktop-header">
      <h1 class="title">Whos <span class="title-hot">Hot</span> Hockey</h1>
      <div class="search-container">
        <SearchBar />
      </div>
    </div>

    <!-- Mobile header: title above search bar, both centered -->
    <div v-else class="header mobile-header">
      <h1 class="title mobile-title">Whos <span class="title-hot">Hot</span> Hockey</h1>
      <div class="search-container">
        <SearchBar />
      </div>
    </div>

    <!-- Latest game day's scores and point scorers -->
    <ScoreTicker />

    <!-- Desktop: grid of cards, rearranged by dragging one onto another -->
    <div v-if="!isMobile" class="content-container desktop-content">
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
    <div
      v-else
      class="content-container mobile-content"
      :style="mobileListHeight ? { '--mobile-list-height': `${mobileListHeight}px` } : null"
    >
      <div
        class="swipe-container"
        ref="swipeContainer"
        @touchstart="onTouchStart"
        @touchmove="onTouchMove"
        @touchend="onTouchEnd"
        @touchcancel="onTouchCancel"
      >
        <div class="swipe-track" :class="{ dragging: isSwiping || !animateSlides }" :style="{ left: `${swipeOffset}px` }">
          <div v-for="(card, index) in mobileCards" :key="card.key" class="swipe-slide">
            <ExpandableCard :title="card.title" :class="{ 'favorites-card': card.key === 'favorites' }">
              <component :is="card.component" />
            </ExpandableCard>
          </div>
        </div>
      </div>
      <div ref="swipeDots" class="swipe-dots">
        <span
          v-for="(card, index) in mobileCards"
          :key="card.key"
          :class="['dot', { active: index === activeCardIndex }]"
          @click="goToCard(index)"
        ></span>
      </div>
    </div>

    <section class="home-intro" aria-labelledby="nhl-stats-heading">
      <h2 id="nhl-stats-heading">NHL stats, hot players and point streaks</h2>
      <p>{{ HOME_DESCRIPTION }}</p>
      <router-link to="/tips">Learn how to use the NHL stats tables</router-link>
    </section>

    <!-- Cookie Consent Banner -->
    <CookieConsent />
  </div>
</template>

<script>
// Module-level so the mobile carousel reopens on the same card after visiting
// a player or team page and coming back
let lastCardKey = null
</script>

<script setup>
import { HOME_DESCRIPTION } from '../seo.js'
import { computed, onMounted, ref, onUnmounted, nextTick, watch, shallowRef } from 'vue'
import ExpandableCard from '../components/ExpandableCard.vue'
import TopPointsTable from '../components/TopPointsTable.vue'
import PointStreaksTable from '../components/PointStreaksTable.vue'
import HottestPlayersTable from '../components/HottestPlayersTable.vue'
import TeamStandingsTable from '../components/TeamStandingsTable.vue'
import TeamWinStreaksTable from '../components/TeamWinStreaksTable.vue'
import TeamHotTable from '../components/TeamHotTable.vue'
import SearchBar from '../components/SearchBar.vue'
import ScoreTicker from '../components/ScoreTicker.vue'
import FavoritesTable from '../components/FavoritesTable.vue'
import CookieConsent from '../components/CookieConsent.vue'
import { useFavorites } from '../composables/useFavorites'
import { useIsMobile } from '../composables/useIsMobile'
import { useSettings, HOME_CARDS, normalizeOrder } from '../composables/useSettings'

const { initializeFavorites, favoritesCount, consentGiven } = useFavorites()
const { mobileCardOrder } = useSettings()

// Show favorites card only if user has favorites
const showFavorites = computed(() => favoritesCount.value > 0)

// Mobile swipe state
const activeCardIndex = ref(0)
const swipeContainer = ref(null)
const touchStartX = ref(0)
const touchCurrentX = ref(0)
const isSwiping = ref(false)
const slideWidth = ref(0)
// Off until the first layout, so a restored card shows without sliding in
const animateSlides = ref(false)

// Mobile: the card lists grow to fill the screen, so the dots end just above
// the fixed footer and scroll-down arrow instead of leaving dead space on
// taller phones
const { isMobile } = useIsMobile()
const homePage = ref(null)
const swipeDots = ref(null)
const mobileListHeight = ref(null)
const MIN_LIST_HEIGHT = 240
// Room above the footer for the app's scroll-down arrowhead (0.75rem up,
// 1.1rem tall). Kept small: any more and the links below would peek out above
// the footer, which hides the arrow.
const SCROLL_HINT_SPACE_REM = 1.75
let layoutObserver = null
let footerEl = null

const updateMobileListHeight = () => {
  const lists = swipeContainer.value?.querySelectorAll('.card-content')
  if (!isMobile.value || !lists?.length || !swipeDots.value) return
  const top = lists[0].getBoundingClientRect().top
  const bottom = Math.max(...[...lists].map(list => list.getBoundingClientRect().bottom))
  // Card padding and dots below the lists, which stay put as the lists grow
  const below = swipeDots.value.getBoundingClientRect().bottom - bottom
  const rem = parseFloat(getComputedStyle(document.documentElement).fontSize)
  const footer = (footerEl?.offsetHeight ?? 0) + SCROLL_HINT_SPACE_REM * rem
  // clientHeight is the viewport with the browser bars shown, so the height
  // doesn't jump as they slide away while scrolling
  const available = document.documentElement.clientHeight - footer - (top + window.scrollY) - below
  mobileListHeight.value = Math.max(MIN_LIST_HEIGHT, Math.floor(available))
}

// The carousel is only mounted on phones, so it is measured once it appears
watch(isMobile, () => nextTick(() => {
  updateSlideWidth()
  updateMobileListHeight()
}))

const COMPONENTS = {
  favorites: FavoritesTable,
  standings: TopPointsTable,
  streaks: PointStreaksTable,
  'hot-players': HottestPlayersTable,
  'team-standings': TeamStandingsTable,
  'win-streaks': TeamWinStreaksTable,
  'team-hot': TeamHotTable
}

const CARDS = HOME_CARDS.map(card => ({ ...card, component: COMPONENTS[card.key] }))
const cardByKey = (key) => CARDS.find(card => card.key === key)

// The favorites card only shows once something has been favorited
const visibleCards = (cards) =>
  cards.filter(card => card.key !== 'favorites' || showFavorites.value)

// Mobile card order is picked on the settings page
const mobileCards = computed(() => visibleCards(mobileCardOrder.value.map(cardByKey)))

// Desktop card order, changed by drag and drop. Saved alongside favorites,
// so it is only remembered between visits when storage consent was given.
const CARD_ORDER_KEY = 'nhl_card_order'
const cardOrder = ref(CARDS.map(card => card.key))

const desktopCards = computed(() =>
  visibleCards(cardOrder.value.map(cardByKey))
)

const loadCardOrder = () => {
  if (consentGiven.value !== true) return
  try {
    cardOrder.value = normalizeOrder(JSON.parse(localStorage.getItem(CARD_ORDER_KEY) || '[]'))
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
// A quick flick changes card even when it is short: at least this fast
// (px/ms, measured over the last FLICK_WINDOW_MS) and this far
const FLICK_VELOCITY = 0.3
const FLICK_MIN_DISTANCE = 20
const FLICK_WINDOW_MS = 100
let touchStartY = 0
let tracking = false
// Recent { x, t } touch positions, for the flick speed
let samples = []

const onTouchStart = (e) => {
  // An expanded card is an overlay; scrolling inside it must not change slides.
  if (e.target.closest('.expandable-card.expanded, .backdrop')) return
  touchStartX.value = e.touches[0].clientX
  touchCurrentX.value = e.touches[0].clientX
  touchStartY = e.touches[0].clientY
  samples = [{ x: e.touches[0].clientX, t: e.timeStamp }]
  tracking = true
}

const onTouchMove = (e) => {
  if (!tracking) return
  const x = e.touches[0].clientX
  // Only the last FLICK_WINDOW_MS count, so resting the finger before a flick
  // does not slow it down. Moves before the swipe locks in are kept too.
  samples.push({ x, t: e.timeStamp })
  while (samples.length > 2 && e.timeStamp - samples[0].t > FLICK_WINDOW_MS) samples.shift()
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
  // Keep the browser from also scrolling the page while the cards move. A
  // diagonal swipe would otherwise start a page fling, and a quick tap on the
  // next card would only stop that fling instead of opening the player/team.
  if (e.cancelable) e.preventDefault()
  touchCurrentX.value = x
}

const onTouchEnd = (e) => {
  tracking = false
  if (!isSwiping.value) return
  isSwiping.value = false
  const diff = touchCurrentX.value - touchStartX.value
  const threshold = slideWidth.value * 0.2
  const first = samples[0]
  const last = samples[samples.length - 1]
  const elapsed = last.t - first.t
  // A finger that stopped before lifting is not a flick
  const velocity = elapsed > 0 && e.timeStamp - last.t < FLICK_WINDOW_MS
    ? (last.x - first.x) / elapsed
    : 0
  const fast = Math.abs(velocity) >= FLICK_VELOCITY
  // -1 shows the next card, 1 the previous, 0 stays: a flick decides on its
  // own, a flick back towards the start cancels, otherwise the distance does
  let direction = 0
  if (fast && Math.abs(diff) >= FLICK_MIN_DISTANCE && Math.sign(velocity) === Math.sign(diff)) {
    direction = Math.sign(diff)
  } else if (!fast && Math.abs(diff) > threshold) {
    direction = Math.sign(diff)
  }

  if (direction < 0 && activeCardIndex.value < mobileCards.value.length - 1) {
    activeCardIndex.value++
  } else if (direction > 0 && activeCardIndex.value > 0) {
    activeCardIndex.value--
  }
}

// The browser or system took the touch over: snap back, never change card
const onTouchCancel = () => {
  tracking = false
  isSwiping.value = false
}

const goToCard = (index) => {
  activeCardIndex.value = index
}

// Initialize favorites on mount
onMounted(() => {
  initializeFavorites()
  loadCardOrder()
  const restored = mobileCards.value.findIndex(card => card.key === lastCardKey)
  if (restored !== -1) activeCardIndex.value = restored
  nextTick(() => {
    updateSlideWidth()
    updateMobileListHeight()
    // Two frames: the restored position is painted before transitions return
    requestAnimationFrame(() => requestAnimationFrame(() => { animateSlides.value = true }))
  })
  window.addEventListener('resize', updateSlideWidth)
  // Re-measures as the score ticker, tables and footer text load in (the
  // page has no min-height on mobile, so it grows with its content)
  footerEl = document.querySelector('.app-footer')
  layoutObserver = new ResizeObserver(updateMobileListHeight)
  layoutObserver.observe(homePage.value)
  if (footerEl) layoutObserver.observe(footerEl)
  window.addEventListener('resize', updateMobileListHeight)
})

onUnmounted(() => {
  // Saved on leaving, by key: the favorites card may appear or vanish before
  // coming back, which shifts the indexes
  lastCardKey = mobileCards.value[activeCardIndex.value]?.key ?? null
  window.removeEventListener('resize', updateSlideWidth)
  window.removeEventListener('resize', updateMobileListHeight)
  layoutObserver?.disconnect()
})
</script>

<style scoped>
.home-intro {
  max-width: 1000px;
  margin: 1.5rem auto;
  padding: 0 1.5rem;
  text-align: center;
}
.home-intro h2 { font-size: 1.2rem; }
.home-intro p { line-height: 1.6; }
.home-intro a { color: var(--color-text-secondary); }

.home-page {
  width: 100%;
  min-height: 100vh;
  background-color: var(--color-bg-primary);
}

/* Header base styles */
.header {
  border-bottom: var(--color-border-thick) solid var(--color-line);
  background-color: var(--color-bg-card);
}

.desktop-header {
  display: flex;
  align-items: center;
  padding: 1.5rem 1rem;
  gap: 2rem;
}

.mobile-header {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 1rem 1.5rem;
  gap: 1rem;
}

.title {
  font-size: var(--site-title-size);
  font-weight: bold;
  font-style: var(--title-style);
  line-height: 1.1;
  color: var(--color-text-primary);
  letter-spacing: calc(3 * var(--tracking));
  text-transform: uppercase;
  margin: 0;
  padding-right: 3rem;
  border-right: var(--color-border-thick) solid var(--color-line);
  flex-shrink: 0;
}

.title-hot {
  color: var(--color-title-hot);
}

.mobile-title {
  font-size: calc(var(--site-title-size) * 0.875);
  padding-right: 0;
  border-right: none;
}

.search-container {
  flex: 1;
  max-width: 500px;
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
  border: var(--color-border-thick) solid var(--color-favorite);
  box-shadow: 0 4px 12px color-mix(in srgb, var(--color-favorite) 20%, transparent);
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
  .mobile-header .search-container {
    width: 100%;
    max-width: 100%;
  }

  .mobile-content {
    padding: 1rem 0.5rem;
  }

  /* Sized by its content, so a resize observer sees the sections load in */
  .home-page {
    min-height: 0;
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

  .mobile-content .swipe-slide :deep(.expandable-card:not(.expanded) .card-content) {
    max-height: var(--mobile-list-height, 300px);
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
    /* Natural width, so the next game badge gets the space left over */
    flex: 0 1 auto;
  }

  /* No hover tooltip on phones: the next game sits at the right of the name, when it fits */
  .mobile-content .swipe-slide :deep(.next-game-badge) {
    display: flex;
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
