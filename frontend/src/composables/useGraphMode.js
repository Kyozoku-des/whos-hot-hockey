import { ref } from 'vue'

// How the game log graphs plot points: 'cumulative' (running season total) or
// 'perGame' (points in each game). Module-level so the choice carries over
// between player and team pages for the rest of the visit.
const graphMode = ref('cumulative')

// Same colors as the home page: highlight text (--color-text-secondary) and
// the score ticker's red (--color-ticker-highlight). Canvas can't read CSS vars.
export const GRAPH_ORANGE = 'hsl(40, 100%, 75%)'
export const GRAPH_ORANGE_FILL = 'hsla(40, 100%, 75%, 0.1)'
const GRAPH_RED = 'hsl(0, 85%, 62%)'

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
    borderColor: GRAPH_RED,
    backgroundColor: GRAPH_RED,
    pointStyle: 'crossRot',
    // Spans 8px, the size of the legend and tooltip squares
    pointRadius: 4,
    pointHoverRadius: 4,
    pointBorderWidth: 2,
    pointHoverBorderWidth: 2
  }
}

// Tooltip color square: small and solid in the line color, like the legend
export const tooltipColorBox = {
  boxWidth: 8,
  boxHeight: 8,
  boxPadding: 4
}

export function tooltipLabelColor(context) {
  const color = context.dataset.borderColor
  return { borderColor: color, backgroundColor: color, borderWidth: 0 }
}

// Phones: a tap shows the tooltip, and any tap while it is open hides it again,
// on the chart or anywhere else on the page. Touch events are left out so a tap
// is a single click.
export const tapTooltipEvents = ['click', 'touchmove']

const isTapMode = (chart) => !chart.options.events.includes('mousemove')

const hideTooltip = (chart) => {
  chart.tooltip.setActiveElements([], { x: 0, y: 0 })
  chart.setActiveElements([])
}

// Inline plugins run after the built-in tooltip plugin, so afterEvent can undo
// the tooltip the same click just showed
export const tapToggleTooltip = {
  id: 'tapToggleTooltip',
  afterInit(chart) {
    chart.$hideOnOutsideTap = (event) => {
      if (!isTapMode(chart) || event.target === chart.canvas) return
      if (chart.tooltip.getActiveElements().length === 0) return
      hideTooltip(chart)
      chart.update()
    }
    document.addEventListener('pointerdown', chart.$hideOnOutsideTap)
  },
  afterDestroy(chart) {
    document.removeEventListener('pointerdown', chart.$hideOnOutsideTap)
  },
  beforeEvent(chart) {
    chart.$tooltipWasShown = chart.tooltip.getActiveElements().length > 0
  },
  afterEvent(chart, args) {
    if (!isTapMode(chart) || args.event.type !== 'click' || !chart.$tooltipWasShown) return
    hideTooltip(chart)
    args.changed = true
  }
}
