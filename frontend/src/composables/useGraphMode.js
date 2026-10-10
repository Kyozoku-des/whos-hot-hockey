import { ref } from 'vue'

// How the game log graphs plot points: 'cumulative' (running season total) or
// 'perGame' (points in each game). Module-level so the choice carries over
// between player and team pages for the rest of the visit.
const graphMode = ref('cumulative')

export function useGraphMode() {
  return { graphMode }
}

// Running total of a per-game series, e.g. [1, 0, 2] -> [1, 1, 3]
export function toCumulative(values) {
  let total = 0
  return values.map(value => (total += value))
}

// Single-point dataset marking the season total a player/team is on pace for
// at the last game of the season. Only meaningful on the cumulative graph.
export function projectionMarker(projectedPoints, seasonGames) {
  const data = Array(seasonGames).fill(null)
  data[seasonGames - 1] = projectedPoints
  return {
    label: 'On Pace',
    data,
    projected: true,
    showLine: false,
    borderColor: '#FFAA00',
    backgroundColor: '#FFAA00',
    pointStyle: 'rectRot',
    pointRadius: 6,
    pointHoverRadius: 8
  }
}
