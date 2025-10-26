<template>
  <div class="home-page">
    <div class="header">
      <h1 class="title">WHOS HOT NHL</h1>
      <div class="search-container">
        <SearchBar />
      </div>
      <CurrentSeasonDisplay />
    </div>

    <div class="content-container">
      <div class="cards-grid">
        <!-- Favorites Card - Only show if user has favorites -->
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

    <!-- Cookie Consent Banner -->
    <CookieConsent />
  </div>
</template>

<script setup>
import { computed, onMounted } from 'vue'
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

// Initialize favorites on mount
onMounted(() => {
  initializeFavorites()
})
</script>

<style scoped>
.home-page {
  width: 100%;
  min-height: 100vh;
  background-color: var(--color-bg-primary);
}

.header {
  display: flex;
  align-items: center;
  padding: 0rem 1rem;
  border-bottom: var(--color-border-thick) solid var(--color-border);
  gap: 2rem;
  background-color: var(--color-bg-card);
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

.header :deep(.current-season-display) {
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

@media (max-width: 1200px) {
  .cards-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 1024px) {
  .header {
    flex-wrap: wrap;
  }

  .search-container {
    order: 3;
    flex-basis: 100%;
    max-width: 100%;
  }
}

@media (max-width: 768px) {
  .header {
    flex-direction: column;
    align-items: flex-start;
    gap: 1rem;
    padding: 1rem 1.5rem;
  }

  .title {
    font-size: 1.8rem;
    border-right: none;
    padding-right: 0;
  }

  .search-container {
    width: 100%;
  }

  .content-container {
    padding: 1.5rem;
  }

  .cards-grid {
    grid-template-columns: 1fr;
    gap: 1rem;
  }
}
</style>
