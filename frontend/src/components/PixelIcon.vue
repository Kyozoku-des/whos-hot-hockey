<template>
  <!-- Retro: pixel grids to match the blocky font. Other themes: smooth line icons -->
  <svg
    v-if="!pixelated"
    class="pixel-icon"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="2.2"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
  >
    <path
      v-for="(d, index) in smoothPaths"
      :key="index"
      :d="d"
      :class="{ 'pixel-icon-fill': name === 'star' }"
      :style="name === 'star' && filled ? { fillOpacity: 1 } : null"
      :fill="name === 'star' ? 'currentColor' : 'none'"
    />
  </svg>
  <svg
    v-else
    class="pixel-icon"
    :viewBox="`0 0 ${width} ${height}`"
    shape-rendering="crispEdges"
    fill="currentColor"
    aria-hidden="true"
  >
    <path :d="outlinePath" />
    <path
      v-if="innerPath"
      class="pixel-icon-fill"
      :d="innerPath"
      :style="filled ? { fillOpacity: 1 } : null"
    />
  </svg>
</template>

<script setup>
import { computed } from 'vue'
import { useSettings } from '../composables/useSettings'

// Icons drawn as pixel grids so they match the blocky Minecraft font.
// For outlined icons, interior pixels go in a separate path whose opacity is
// controlled by the `filled` prop or the --pixel-icon-fill CSS variable.
const ICONS = {
  search: {
    outlined: false,
    rows: [
      '..####....',
      '.#....#...',
      '#......#..',
      '#......#..',
      '#......#..',
      '#......#..',
      '.#....#...',
      '..####.#..',
      '.......##.',
      '........##'
    ]
  },
  close: {
    outlined: false,
    rows: [
      '##...##',
      '###.###',
      '.#####.',
      '..###..',
      '.#####.',
      '###.###',
      '##...##'
    ]
  },
  expand: {
    outlined: false,
    rows: [
      '.######',
      '.....##',
      '....###',
      '...##.#',
      '..##..#',
      '.##....',
      '##.....'
    ]
  },
  'arrow-down': {
    outlined: false,
    rows: [
      '...###...',
      '...###...',
      '...###...',
      '...###...',
      '#########',
      '.#######.',
      '..#####..',
      '...###...',
      '....#....'
    ]
  },
  'chevron-down': {
    outlined: false,
    rows: [
      '##.....##',
      '###...###',
      '.###.###.',
      '..#####..',
      '...###...',
      '....#....'
    ]
  },
  star: {
    outlined: true,
    rows: [
      '.....#.....',
      '....###....',
      '....###....',
      '###########',
      '.#########.',
      '..#######..',
      '..#######..',
      '.####.####.',
      '.###...###.',
      '##.......##'
    ]
  }
}

// Same names as ICONS, on a 24x24 grid
const SMOOTH_ICONS = {
  search: ['M11 4a7 7 0 1 0 0 14a7 7 0 1 0 0-14z', 'm20 20-3.5-3.5'],
  close: ['M6 6l12 12', 'M18 6 6 18'],
  expand: ['M15 3h6v6', 'M9 21H3v-6', 'M21 3l-7 7', 'M3 21l7-7'],
  'arrow-down': ['M12 4v15', 'm19 12-7 7-7-7'],
  'chevron-down': ['m5 9 7 7 7-7'],
  star: ['M12 2.8l2.85 5.8 6.4.93-4.63 4.5 1.1 6.37L12 17.4l-5.72 3 1.1-6.37-4.63-4.5 6.4-.93z']
}

const { theme } = useSettings()
const pixelated = computed(() => theme.value === 'retro')

const props = defineProps({
  name: {
    type: String,
    required: true
  },
  filled: {
    type: Boolean,
    default: false
  }
})

const smoothPaths = computed(() => SMOOTH_ICONS[props.name])
const icon = computed(() => ICONS[props.name])
const height = computed(() => icon.value.rows.length)
const width = computed(() => icon.value.rows[0].length)

const isSet = (rows, x, y) => rows[y]?.[x] === '#'

const pixelPaths = computed(() => {
  const { rows, outlined } = icon.value
  let outline = ''
  let inner = ''
  rows.forEach((row, y) => {
    for (let x = 0; x < row.length; x++) {
      if (!isSet(rows, x, y)) continue
      const pixel = `M${x} ${y}h1v1h-1z`
      const isEdge = !isSet(rows, x - 1, y) || !isSet(rows, x + 1, y) ||
        !isSet(rows, x, y - 1) || !isSet(rows, x, y + 1)
      if (!outlined || isEdge) {
        outline += pixel
      } else {
        inner += pixel
      }
    }
  })
  return { outline, inner }
})

const outlinePath = computed(() => pixelPaths.value.outline)
const innerPath = computed(() => pixelPaths.value.inner)
</script>

<style scoped>
.pixel-icon {
  display: block;
  width: 1em;
  height: 1em;
}

.pixel-icon-fill {
  fill-opacity: var(--pixel-icon-fill, 0);
}
</style>
