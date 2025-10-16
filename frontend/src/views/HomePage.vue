<template>
  <div class="home-page">
    <div class="container">
      <div class="header-section">
        <h1 class="page-title">NHL Statistics</h1>
        <SeasonSelector v-model="selectedSeason" @change="handleSeasonChange" />
      </div>

      <div class="tables-grid">
        <TopPointsTable :key="selectedSeason" />
        <PointStreaksTable :key="selectedSeason" />
        <HottestPlayersTable :key="selectedSeason" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, provide } from 'vue'
import TopPointsTable from '../components/TopPointsTable.vue'
import PointStreaksTable from '../components/PointStreaksTable.vue'
import HottestPlayersTable from '../components/HottestPlayersTable.vue'
import SeasonSelector from '../components/SeasonSelector.vue'

const selectedSeason = ref(null)

// Provide season to all child components
provide('selectedSeason', selectedSeason)

const handleSeasonChange = (season) => {
  console.log('Season changed to:', season)
}
</script>

<style scoped>
.home-page {
  width: 100%;
}

.container {
  max-width: 1400px;
  margin: 0 auto;
  padding: 0 2rem;
}

.header-section {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 2rem;
  padding-bottom: 1rem;
  border-bottom: 2px solid rgba(255, 255, 255, 0.1);
}

.page-title {
  font-size: 2rem;
  font-weight: 700;
  color: #fff;
  margin: 0;
}

.tables-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(350px, 1fr));
  gap: 2rem;
  margin-bottom: 2rem;
}

@media (max-width: 768px) {
  .header-section {
    flex-direction: column;
    align-items: flex-start;
    gap: 1rem;
  }

  .tables-grid {
    grid-template-columns: 1fr;
  }
}
</style>
