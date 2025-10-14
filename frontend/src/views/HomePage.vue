<template>
  <div class="home-page">
    <div class="container">
      <div class="tables-grid">
        <TopPointsTable />
        <PointStreaksTable />
        <HottestPlayersTable />
      </div>
    </div>
  </div>
</template>

<script setup>
import TopPointsTable from '../components/TopPointsTable.vue'
import PointStreaksTable from '../components/PointStreaksTable.vue'
import HottestPlayersTable from '../components/HottestPlayersTable.vue'
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

.tables-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(350px, 1fr));
  gap: 2rem;
  margin-bottom: 2rem;
}

@media (max-width: 768px) {
  .tables-grid {
    grid-template-columns: 1fr;
  }
}
</style>
