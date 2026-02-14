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

    <!-- Mobile header: logo above search bar, both centered -->
    <div class="header mobile-header">
      <img src="../assets/nhl_logo.png" alt="NHL Logo" class="mobile-logo" />
      <div class="search-container">
        <SearchBar />
      </div>
    </div>

    <!-- Desktop: grid of cards -->
    <div class="content-container desktop-content">
      <div class="cards-grid">
        <ExpandableCard v-if="showFavorites" title="My Favorites" :defaultExpanded="true" class="favorites-card">
          <FavoritesTable />
        </ExpandableCard>

        <ExpandableCard title="Player standings">
          <TopPointsTable />
        </ExpandableCard>

        <ExpandableCard title="Point streaks">
          <PointStreaksTable />
        </ExpandableCard>

        <ExpandableCard title="Last 10 games">
          <HottestPlayersTable />
        </ExpandableCard>

        <ExpandableCard title="Team standings">
          <TeamStandingsTable />
        </ExpandableCard>

        <ExpandableCard title="Win streaks">
          <TeamWinStreaksTable />
        </ExpandableCard>

        <ExpandableCard title="Last 10 games">
          <TeamHotTable />
        </ExpandableCard>
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
        <div class="swipe-track" :style="{ transform: `translateX(${swipeOffset}px)` }">
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
import FavoritesTable from '../components/FavoritesTable.vue'
import CookieConsent from '../components/CookieConsent.vue'
import { useFavorites } from '../composables/useFavorites'

const { initializeFavorites, favoritesCount } = useFavorites()

// Show favorites card only if user has favorites
const showFavorites = computed(() => favoritesCount.value > 0)

// Mobile swipe state
const activeCardIndex = ref(0)
const swipeContainer = ref(null)
const touchStartX = ref(0)
const touchCurrentX = ref(0)
const isSwiping = ref(false)
const slideWidth = ref(0)

const mobileCards = computed(() => {
  const cards = []
  if (showFavorites.value) {
    cards.push({ key: 'favorites', title: 'My Favorites', component: FavoritesTable })
  }
  cards.push(
    { key: 'standings', title: 'Player standings', component: TopPointsTable },
    { key: 'streaks', title: 'Point streaks', component: PointStreaksTable },
    { key: 'hot-players', title: 'Last 10 games', component: HottestPlayersTable },
    { key: 'team-standings', title: 'Team standings', component: TeamStandingsTable },
    { key: 'win-streaks', title: 'Win streaks', component: TeamWinStreaksTable },
    { key: 'team-hot', title: 'Last 10 games', component: TeamHotTable }
  )
  return cards
})

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

const onTouchStart = (e) => {
  touchStartX.value = e.touches[0].clientX
  touchCurrentX.value = e.touches[0].clientX
  isSwiping.value = true
}

const onTouchMove = (e) => {
  if (!isSwiping.value) return
  touchCurrentX.value = e.touches[0].clientX
}

const onTouchEnd = () => {
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

.mobile-logo {
  width: 80px;
  height: auto;
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
}

.swipe-track {
  display: flex;
  transition: transform 0.3s ease;
  will-change: transform;
}

.swipe-slide {
  min-width: 100%;
  flex-shrink: 0;
  padding: 0 0.5rem;
  box-sizing: border-box;
}

.swipe-slide :deep(.expandable-card) {
  height: auto;
  min-height: 400px;
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
}
</style>
