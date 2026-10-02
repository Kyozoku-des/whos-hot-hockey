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
