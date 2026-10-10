<template>
  <div class="game-log-graph">
    <h3 class="graph-title">{{ graphTitle }}</h3>
    <GraphModeToggle />
    <div class="chart-wrapper">
      <Line v-if="hasData" :data="combinedChartData" :options="chartOptions" />
      <div v-else class="no-data">No game log data available</div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import GraphModeToggle from './GraphModeToggle.vue'
import { useGraphMode, toCumulative } from '../composables/useGraphMode'
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
  },
  // Season id the previous-season data was fetched for, e.g. "20242025".
  // Labels derive from it so they always match the data actually shown.
  previousSeason: {
    type: String,
    default: ''
  }
})

const previousSeasonStart = computed(() => Number(props.previousSeason.slice(0, 4)))

const previousSeasonLabel = computed(() =>
  `${previousSeasonStart.value}-${previousSeasonStart.value + 1}`
)

const currentSeasonLabel = computed(() =>
  `${previousSeasonStart.value + 1}-${previousSeasonStart.value + 2}`
)

const { graphMode } = useGraphMode()
const isCumulative = computed(() => graphMode.value === 'cumulative')

const graphTitle = computed(() =>
  isCumulative.value
    ? 'Season Points - Season Comparison'
    : 'Points Per Game - Season Comparison'
)

// Check if we have any data
const hasData = computed(() => {
  return (props.currentSeasonData && props.currentSeasonData.length > 0) ||
         (props.previousSeasonData && props.previousSeasonData.length > 0)
})

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
    const previousPoints = props.previousSeasonData.map(game => game.points || 0)
    const previousGoals = props.previousSeasonData.map(game => game.goals || 0)
    const previousAssists = props.previousSeasonData.map(game => game.assists || 0)
    datasets.push({
      label: `${previousSeasonLabel.value} (Previous)`,
      data: isCumulative.value ? toCumulative(previousPoints) : previousPoints,
      points: previousPoints,
      totals: toCumulative(previousPoints),
      goals: previousGoals,
      assists: previousAssists,
      borderColor: '#6B7280',
      backgroundColor: 'rgba(107, 114, 128, 0.1)',
      borderWidth: 2,
      tension: 0.1,
      pointStyle: 'cross',
      pointRadius: 4,
      pointHoverRadius: 6,
      pointBorderWidth: 2,
      borderDash: [5, 5] // Dashed line for previous season
    })
  }

  // Add current season data (if available)
  if (props.currentSeasonData && props.currentSeasonData.length > 0) {
    const currentPoints = props.currentSeasonData.map(game => game.points || 0)
    const currentGoals = props.currentSeasonData.map(game => game.goals || 0)
    const currentAssists = props.currentSeasonData.map(game => game.assists || 0)
    datasets.push({
      label: `${currentSeasonLabel.value} (Current)`,
      data: isCumulative.value ? toCumulative(currentPoints) : currentPoints,
      points: currentPoints,
      totals: toCumulative(currentPoints),
      goals: currentGoals,
      assists: currentAssists,
      borderColor: '#FFAA00',
      backgroundColor: 'rgba(255, 170, 0, 0.1)',
      borderWidth: 3,
      tension: 0.1,
      pointStyle: 'cross',
      pointRadius: 5,
      pointHoverRadius: 7,
      pointBorderWidth: 2
    })
  }

  return {
    labels,
    datasets
  }
})

const chartOptions = computed(() => ({
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
        // Solid plus signs in the legend, even for the dashed previous-season line
        generateLabels: (chart) =>
          ChartJS.defaults.plugins.legend.labels.generateLabels(chart).map(item => ({ ...item, lineDash: [] })),
        padding: 15,
        font: {
          family: 'Minecraft, sans-serif',
          size: 12
        }
      }
    },
    tooltip: {
      mode: 'index',
      intersect: false,
      titleFont: {
        family: 'Minecraft, sans-serif',
        size: 13
      },
      bodyFont: {
        family: 'Minecraft, sans-serif',
        size: 12
      },
      callbacks: {
        title: (context) => {
          return `Game ${context[0].label}`
        },
        label: (context) => {
          const gameIndex = context.dataIndex
          const dataset = context.dataset
          const points = dataset.points?.[gameIndex] ?? 0
          const total = dataset.totals?.[gameIndex] ?? 0

          // Get goals and assists if available in the dataset
          const goals = dataset.goals?.[gameIndex] ?? 0
          const assists = dataset.assists?.[gameIndex] ?? 0

          return `${dataset.label}: ${goals}G, ${assists}A, ${points}P (Total: ${total}P)`
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
          family: 'Minecraft, sans-serif',
          size: 14,
          weight: 'bold'
        }
      },
      ticks: {
        color: '#ffffff',
        maxTicksLimit: 20,
        font: {
          family: 'Minecraft, sans-serif'
        }
      },
      grid: {
        color: 'rgba(255, 255, 255, 0.1)'
      }
    },
    y: {
      title: {
        display: true,
        text: isCumulative.value ? 'Total Points' : 'Points',
        color: '#ffffff',
        font: {
          family: 'Minecraft, sans-serif',
          size: 14,
          weight: 'bold'
        }
      },
      ticks: {
        color: '#ffffff',
        // Totals climb past 100, so only force single steps per game
        stepSize: isCumulative.value ? undefined : 1,
        precision: 0,
        font: {
          family: 'Minecraft, sans-serif'
        }
      },
      grid: {
        color: 'rgba(255, 255, 255, 0.1)'
      },
      beginAtZero: true,
      // Headroom so the highest points don't touch the top of the chart
      grace: '10%'
    }
  }
}))
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
