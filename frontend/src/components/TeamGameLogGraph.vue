<template>
  <div class="game-log-graph">
    <h3 class="graph-title">Cumulative Wins - Season Comparison</h3>
    <div class="chart-wrapper">
      <Line v-if="hasData" :data="combinedChartData" :options="chartOptions" />
      <div v-else class="no-data">No game log data available</div>
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
  return `${seasonStart}-${seasonEnd}`
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
  return `${seasonStart}-${seasonEnd}`
})

// Check if we have any data
const hasData = computed(() => {
  return (props.currentSeasonData && props.currentSeasonData.length > 0) ||
         (props.previousSeasonData && props.previousSeasonData.length > 0)
})

// Calculate cumulative wins from game data
const calculateCumulativeWins = (gameData) => {
  let cumulativeWins = 0
  return gameData.map(game => {
    if (game.won) {
      cumulativeWins++
    }
    return cumulativeWins
  })
}

// Combine both seasons into one chart with two datasets
const combinedChartData = computed(() => {
  // Determine max game count (82 for full season, or longest available)
  const maxGames = Math.max(
    82,
    props.currentSeasonData?.length || 0,
    props.previousSeasonData?.length || 0
  )

  // Create labels (1-82)
  const labels = Array.from({ length: maxGames }, (_, i) => i + 1)

  const datasets = []

  // Add previous season data (if available)
  if (props.previousSeasonData && props.previousSeasonData.length > 0) {
    const previousWins = calculateCumulativeWins(props.previousSeasonData)
    datasets.push({
      label: `${previousSeasonLabel.value} (Previous)`,
      data: previousWins,
      borderColor: '#6B7280',
      backgroundColor: 'rgba(107, 114, 128, 0.1)',
      borderWidth: 2,
      tension: 0.1,
      pointRadius: 2,
      pointHoverRadius: 5,
      borderDash: [5, 5] // Dashed line for previous season
    })
  }

  // Add current season data (if available)
  if (props.currentSeasonData && props.currentSeasonData.length > 0) {
    const currentWins = calculateCumulativeWins(props.currentSeasonData)
    datasets.push({
      label: `${currentSeasonLabel.value} (Current)`,
      data: currentWins,
      borderColor: '#FFAA00',
      backgroundColor: 'rgba(255, 170, 0, 0.1)',
      borderWidth: 3,
      tension: 0.1,
      pointRadius: 3,
      pointHoverRadius: 6
    })
  }

  return {
    labels,
    datasets
  }
})

const chartOptions = {
  responsive: true,
  maintainAspectRatio: false,
  interaction: {
    mode: 'index',
    intersect: false
  },
  plugins: {
    legend: {
      display: true,
      position: 'top',
      labels: {
        color: '#ffffff',
        usePointStyle: true,
        padding: 15
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
          return `${context.dataset.label}: ${context.parsed.y} wins`
        }
      }
    }
  },
  scales: {
    x: {
      title: {
        display: true,
        text: 'Game Number',
        color: '#ffffff',
        font: {
          size: 14,
          weight: 'bold'
        }
      },
      ticks: {
        color: '#ffffff',
        maxTicksLimit: 20
      },
      grid: {
        color: 'rgba(255, 255, 255, 0.1)'
      }
    },
    y: {
      title: {
        display: true,
        text: 'Cumulative Wins',
        color: '#ffffff',
        font: {
          size: 14,
          weight: 'bold'
        }
      },
      ticks: {
        color: '#ffffff',
        stepSize: 5
      },
      grid: {
        color: 'rgba(255, 255, 255, 0.1)'
      },
      beginAtZero: true
    }
  }
}
</script>

<style scoped>
.game-log-graph {
  width: 100%;
  padding: 1.5rem;
  background: var(--color-bg-card);
  border-radius: 8px;
}

.graph-title {
  font-size: 1.5rem;
  font-weight: 700;
  color: var(--color-text-primary);
  text-align: center;
  margin-bottom: 1.5rem;
}

.chart-wrapper {
  height: 400px;
  position: relative;
}

.no-data {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--color-text-secondary);
  font-style: italic;
  font-size: 1.1rem;
}

@media (max-width: 768px) {
  .chart-wrapper {
    height: 300px;
  }

  .graph-title {
    font-size: 1.2rem;
  }
}
</style>
