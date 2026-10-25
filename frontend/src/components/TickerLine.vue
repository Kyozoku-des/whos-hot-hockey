<template>
  <div
    v-if="items.length"
    class="ticker-line"
    :class="{ 'reduced-motion': reducedMotion, held }"
    :aria-label="ariaLabel"
    role="marquee"
    @pointerenter="onPointerEnter"
    @pointerleave="hovered = false"
    @pointerdown="onPointerDown"
    @pointermove="onPointerMove"
    @pointerup="onPointerUp"
    @pointercancel="onPointerUp"
    @click.capture="onClickCapture"
    @focusin="focused = true"
    @focusout="focused = false"
  >
    <div class="ticker-label">{{ label }}</div>
    <div class="ticker-viewport" :ref="setViewport">
      <!-- Two equal halves, each wide enough to fill the line, so the loop scrolls seamlessly;
           only the first copy is read by screen readers -->
      <div class="ticker-track" :ref="setTrack">
        <div
          v-for="copy in copies"
          :key="copy"
          class="ticker-content"
          :ref="el => copy === 0 && setContent(el)"
          :aria-hidden="copy > 0 ? 'true' : undefined"
        >
          <span v-for="item in items" :key="itemKey(item)" class="ticker-item">
            <!-- copy > 0 is a repeat: links in it should leave the tab order -->
            <slot :item="item" :copy="copy" />
          </span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'

/**
 * A line of items that scrolls sideways without end, pauses under the mouse or keyboard
 * focus, and can be dragged and flung by hand. With reduced motion it stays still and
 * scrolls like any overflowing line. Each item is rendered by the default slot, once per
 * copy of the content; the slot's `copy` is 0 for the copy screen readers and the keyboard use.
 */
const props = defineProps({
  items: { type: Array, required: true },
  itemKey: { type: Function, required: true },
  label: { type: String, default: '' },
  ariaLabel: { type: String, default: undefined },
  // Scroll speed in pixels per second
  speed: { type: Number, default: 60 }
})

// After a drag the line glides at the release speed and eases back to the normal speed over about this long
const EASE_SECONDS = 0.8
// Fastest fling, in pixels per second
const MAX_FLING = 4000
// A release this long after the last movement is a stop, not a fling
const FLING_WINDOW_MS = 100
// Pointer travel, in pixels, before a press becomes a drag; a shorter press is a click
const DRAG_THRESHOLD = 4

// Copies of the content in each half of the track
const repeats = ref(1)
const reducedMotion = ref(false)
// Dragged by hand while pressed
const held = ref(false)
// Paused while a mouse is over the line or it has keyboard focus. Only mouse hover
// counts: touch screens keep hover after a tap, which would leave the ticker stopped
const hovered = ref(false)
const focused = ref(false)

// Measured to fill the line and loop the scroll
let content = null
let viewport = null
let track = null
const setContent = (el) => {
  content = el
}
const setViewport = (el) => {
  viewport = el
}
const setTrack = (el) => {
  track = el
}

// How far the line has scrolled left, in pixels, within one half of the track
let offset = 0
// Current speed in pixels per second; positive scrolls left
let velocity = props.speed
// Width of one half of the track, after which the scroll loops
let loopWidth = 0
let frame = null
let lastFrame = null
// Pointer position and time at the last drag movement
let dragX = 0
let dragTime = 0
// Where the press started, and whether it has moved far enough to be a drag
let pressX = 0
let dragged = false
let motionQuery = null

// With reduced motion the content is shown once and scrolled by hand
const copies = computed(() =>
  reducedMotion.value ? [0] : Array.from({ length: repeats.value * 2 }, (_, index) => index)
)

// Repeat short content so no gap shows
const updateLayout = () => {
  const width = content?.offsetWidth
  if (!width) return
  repeats.value = Math.max(1, Math.ceil((viewport?.clientWidth ?? 0) / width))
  loopWidth = repeats.value * width
}

const render = () => {
  if (!track) return
  if (loopWidth) offset = ((offset % loopWidth) + loopWidth) % loopWidth
  track.style.transform = `translate3d(${-offset}px, 0, 0)`
}

// Move the line each frame; after a drag, ease from the release speed back to the
// normal speed, or to a stop while paused
const step = (time) => {
  frame = requestAnimationFrame(step)
  // A long gap (a background tab) would otherwise jump the line
  const seconds = lastFrame === null ? 0 : Math.min(time - lastFrame, 100) / 1000
  lastFrame = time
  if (held.value) return
  const target = hovered.value || focused.value ? 0 : props.speed
  velocity += (target - velocity) * (1 - Math.exp(-seconds / (EASE_SECONDS / 3)))
  offset += velocity * seconds
  render()
}

const startMotion = () => {
  if (frame !== null || reducedMotion.value) return
  lastFrame = null
  frame = requestAnimationFrame(step)
}

const stopMotion = () => {
  if (frame !== null) cancelAnimationFrame(frame)
  frame = null
  if (track) track.style.transform = ''
}

const onPointerEnter = (event) => {
  if (event.pointerType === 'mouse') hovered.value = true
}

// Holding the line lets it be dragged sideways; letting go flings it
const onPointerDown = (event) => {
  if (reducedMotion.value || (event.pointerType === 'mouse' && event.button !== 0)) return
  held.value = true
  dragged = false
  velocity = 0
  pressX = event.clientX
  dragX = event.clientX
  dragTime = event.timeStamp
}

const onPointerMove = (event) => {
  if (!held.value) return
  // Captured only once it is a drag, so a plain click still reaches a link in the line
  if (!dragged && Math.abs(event.clientX - pressX) >= DRAG_THRESHOLD) {
    dragged = true
    event.currentTarget.setPointerCapture?.(event.pointerId)
  }
  const moved = dragX - event.clientX
  const elapsed = event.timeStamp - dragTime
  offset += moved
  render()
  if (elapsed > 0) {
    // Smooth over the last few movements so one jittery event doesn't set the fling
    velocity = 0.8 * ((moved / elapsed) * 1000) + 0.2 * velocity
  }
  dragX = event.clientX
  dragTime = event.timeStamp
}

const onPointerUp = (event) => {
  if (!held.value) return
  held.value = false
  velocity = event.timeStamp - dragTime > FLING_WINDOW_MS
    ? 0
    : Math.max(-MAX_FLING, Math.min(MAX_FLING, velocity))
}

// The end of a drag is not a click on whatever link is under the pointer
const onClickCapture = (event) => {
  if (!dragged) return
  dragged = false
  event.preventDefault()
  event.stopPropagation()
}

// Measure the new content once it is rendered, then scroll it
const layout = async () => {
  await nextTick()
  updateLayout()
  render()
  startMotion()
}

watch(() => props.items, layout)

const onMotionChange = async (event) => {
  reducedMotion.value = event.matches
  if (event.matches) {
    stopMotion()
  } else {
    // The extra copies are rendered again before the loop can be measured
    await nextTick()
    updateLayout()
    startMotion()
  }
}

onMounted(() => {
  motionQuery = window.matchMedia('(prefers-reduced-motion: reduce)')
  reducedMotion.value = motionQuery.matches
  motionQuery.addEventListener('change', onMotionChange)
  window.addEventListener('resize', updateLayout)
  layout()
})

onUnmounted(() => {
  stopMotion()
  motionQuery?.removeEventListener('change', onMotionChange)
  window.removeEventListener('resize', updateLayout)
})
</script>

<style scoped>
.ticker-line {
  display: flex;
  align-items: stretch;
  border-bottom: var(--color-border-thick) solid var(--color-line);
  background-color: var(--ticker-bg, var(--color-bg-card));
  font-size: 0.85rem;
  overflow: hidden;
}

.ticker-label {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  padding: 0.5rem 1rem;
  border-right: var(--color-border-thick) solid var(--color-line);
  color: var(--ticker-label-color, var(--color-text-secondary));
  background-color: var(--ticker-label-bg);
}

.ticker-viewport {
  flex: 1;
  min-width: 0;
  overflow: hidden;
}

.ticker-track {
  display: flex;
  width: max-content;
  will-change: transform;
}

/* Horizontal drags move the line; vertical swipes still scroll the page */
.ticker-line:not(.reduced-motion) {
  cursor: grab;
  touch-action: pan-y;
}

.ticker-line.held {
  cursor: grabbing;
}

/* Dragging the line shouldn't select text or open a menu */
.ticker-line:not(.reduced-motion),
.ticker-line:not(.reduced-motion) * {
  user-select: none;
  -webkit-user-select: none;
  -webkit-touch-callout: none;
}

.ticker-content {
  display: flex;
  flex-shrink: 0;
  white-space: nowrap;
}

.ticker-item {
  display: flex;
  align-items: center;
  padding: 0.5rem 0;
}

/* Separates consecutive items, including the end of one copy and the start of the next */
.ticker-item::after {
  content: '|';
  padding: 0 1.5rem;
  color: var(--color-line);
}

/* No motion: the line stays still and can be scrolled sideways */
.reduced-motion .ticker-viewport {
  overflow-x: auto;
}

@media (max-width: 768px) {
  .ticker-line {
    font-size: 0.75rem;
  }

  .ticker-label {
    padding: 0.5rem 0.75rem;
  }
}
</style>
