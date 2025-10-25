<template>
  <div class="game-log-graph">
    <div class="graphs-container">
      <div class="graph-section">
        <h3 class="graph-title">Current Season ({{ currentSeasonLabel }})</h3>
        <div class="chart-wrapper">
          <Line v-if="currentSeasonData" :data="currentSeasonChartData" :options="chartOptions" />
          <div v-else class="no-data">No current season data available</div>
        </div>
      </div>

      <div class="graph-section">
        <h3 class="graph-title">Previous Season ({{ previousSeasonLabel }})</h3>
        <div class="chart-wrapper">
          <Line v-if="previousSeasonData" :data="previousSeasonChartData" :options="chartOptions" />
          <div v-else class="no-data">No previous season data available</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { Line } from 'vue-chartjs'
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Title,
  Tooltip,
  Legend
} from 'chart.js'

ChartJS.register(
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Title,
  Tooltip,
  Legend
)

const props = defineProps({
  currentSeasonData: {
    type: Array,
    default: () => []
  },
  previousSeasonData: {
    type: Array,
    default: () => []
  }
})

// Calculate current season label
const currentSeasonLabel = computed(() => {
  const currentYear = new Date().getFullYear()
  const currentMonth = new Date().getMonth() + 1

  let seasonStart
  if (currentMonth >= 1 && currentMonth <= 6) {
    seasonStart = currentYear - 1
  } else {
    seasonStart = currentYear
  }

  const seasonEnd = seasonStart + 1
  return `${String(seasonStart).slice(-2)}/${String(seasonEnd).slice(-2)}`
})

// Calculate previous season label
const previousSeasonLabel = computed(() => {
  const currentYear = new Date().getFullYear()
  const currentMonth = new Date().getMonth() + 1

  let seasonStart
  if (currentMonth >= 1 && currentMonth <= 6) {
    seasonStart = currentYear - 2
  } else {
    seasonStart = currentYear - 1
  }

  const seasonEnd = seasonStart + 1
  return `${String(seasonStart).slice(-2)}/${String(seasonEnd).slice(-2)}`
})

// Process current season data
const currentSeasonChartData = computed(() => {
  if (!props.currentSeasonData || props.currentSeasonData.length === 0) return null

  const gameNumbers = props.currentSeasonData.map((_, index) => index + 1)
  const wins = props.currentSeasonData.map(game => game.win ? 1 : 0)

  return {
    labels: gameNumbers,
    datasets: [
      {
        label: 'Win (1) / Loss (0)',
        data: wins,
        borderColor: '#FFAA00',
        backgroundColor: 'rgba(255, 170, 0, 0.1)',
        borderWidth: 2,
        tension: 0.1,
        pointRadius: 3,
        pointHoverRadius: 5,
        stepped: true
      }
    ]
  }
})

// Process previous season data
const previousSeasonChartData = computed(() => {
  if (!props.previousSeasonData || props.previousSeasonData.length === 0) return null

  const gameNumbers = props.previousSeasonData.map((_, index) => index + 1)
  const wins = props.previousSeasonData.map(game => game.win ? 1 : 0)

  return {
    labels: gameNumbers,
    datasets: [
      {
        label: 'Win (1) / Loss (0)',
        data: wins,
        borderColor: '#6B7280',
        backgroundColor: 'rgba(107, 114, 128, 0.1)',
        borderWidth: 2,
        tension: 0.1,
        pointRadius: 3,
        pointHoverRadius: 5,
        stepped: true
      }
    ]
  }
})

const chartOptions = {
  responsive: true,
  maintainAspectRatio: false,
  plugins: {
    legend: {
      display: true,
      labels: {
        color: '#ffffff'
      }
    },
    tooltip: {
      mode: 'index',
      intersect: false,
      callbacks: {
        title: (context) => {
          return `Game ${context[0].label}`
        },
        label: (context) => {
          return context.parsed.y === 1 ? 'Win' : 'Loss'
        }
      }
    }
  },
  scales: {
    x: {
      title: {
        display: true,
        text: 'Game Number',
        color: '#ffffff'
      },
      ticks: {
        color: '#ffffff'
      },
      grid: {
        color: 'rgba(255, 255, 255, 0.1)'
      }
    },
    y: {
      title: {
        display: true,
        text: 'Result',
        color: '#ffffff'
      },
      ticks: {
        color: '#ffffff',
        stepSize: 1,
        callback: function(value) {
          return value === 1 ? 'Win' : value === 0 ? 'Loss' : ''
        }
      },
      grid: {
        color: 'rgba(255, 255, 255, 0.1)'
      },
      min: 0,
      max: 1
    }
  }
}
</script>

<style scoped>
.game-log-graph {
  width: 100%;
}

.graphs-container {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 2rem;
}

.graph-section {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.graph-title {
  font-size: 1.2rem;
  font-weight: 600;
  color: var(--color-text-secondary);
  text-align: center;
}

.chart-wrapper {
  height: 300px;
  position: relative;
}

.no-data {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--color-text-secondary);
  font-style: italic;
}

@media (max-width: 968px) {
  .graphs-container {
    grid-template-columns: 1fr;
  }
}
</style>
