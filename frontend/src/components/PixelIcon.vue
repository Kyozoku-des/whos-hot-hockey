<template>
  <svg
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
