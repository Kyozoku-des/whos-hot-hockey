<template>
  <div class="home-page">
    <div class="header">
      <h1 class="title">WHOS HOT NHL</h1>
      <SeasonSelector v-model="selectedSeason" @change="handleSeasonChange" />
    </div>

    <div class="content-container">
      <div class="cards-grid">
        <ExpandableCard title="Player standings">
          <TopPointsTable />
        </ExpandableCard>

        <ExpandableCard title="Point streaks">
          <PointStreaksTable />
        </ExpandableCard>

        <ExpandableCard title="Whos hot">
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
  </div>
</template>

<script setup>
import { ref, provide } from 'vue'
import ExpandableCard from '../components/ExpandableCard.vue'
import TopPointsTable from '../components/TopPointsTable.vue'
import PointStreaksTable from '../components/PointStreaksTable.vue'
import HottestPlayersTable from '../components/HottestPlayersTable.vue'
import TeamStandingsTable from '../components/TeamStandingsTable.vue'
import TeamWinStreaksTable from '../components/TeamWinStreaksTable.vue'
import TeamHotTable from '../components/TeamHotTable.vue'
import SeasonSelector from '../components/SeasonSelector.vue'

const selectedSeason = ref(null)

provide('selectedSeason', selectedSeason)

const handleSeasonChange = (season) => {
  console.log('Season changed to:', season)
}
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
  gap: 0;
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

.header :deep(.season-selector) {
  margin-left: auto;
}

.content-container {
  padding: 5rem 2rem 2rem 2rem;
}

.cards-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  grid-template-rows: repeat(2, 1fr);
  gap: 1.5rem;
  max-width: 1600px;
  margin: 0 auto;
  height: calc(100vh - 200px);
  min-height: 700px;
}

@media (max-width: 1200px) {
  .cards-grid {
    grid-template-columns: repeat(2, 1fr);
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
