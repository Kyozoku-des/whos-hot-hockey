<template>
  <button
    v-show="visible"
    ref="buttonRef"
    type="button"
    class="favorite-toggle"
    :class="{ favorited: isFav }"
    :disabled="!isFav && !canAddMore()"
    @click.stop="toggle"
    @touchstart="onButtonTouchStart"
    @touchend="onButtonTouchEnd"
  >
    {{ label }}
  </button>
</template>

<script>
import { ref } from 'vue'

// Shared by every row: only one row shows its button after a long press
const pressedId = ref(null)
let pressedRow = null

// A touch anywhere outside the open row closes its button. One listener
// serves every row: with one each, every touch ran hundreds of them.
const onDocumentTouch = (e) => {
  if (pressedId.value !== null && !pressedRow?.contains(e.target)) {
    pressedId.value = null
  }
}

let mountedCount = 0

const listenForOutsideTouch = () => {
  if (mountedCount++ === 0) document.addEventListener('touchstart', onDocumentTouch, { passive: true })
}

const stopListeningForOutsideTouch = () => {
  if (--mountedCount === 0) document.removeEventListener('touchstart', onDocumentTouch)
}
</script>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useFavorites } from '../composables/useFavorites'

// Shows an add/remove favorites button on its parent row: on hover with a
// mouse, or after pressing and holding the row on a touch screen.
const props = defineProps({
  item: {
    type: Object,
    required: true
  }
})

const LONG_PRESS_MS = 500
const MOVE_TOLERANCE = 10

const { isFavorited, toggleFavorite, canAddMore } = useFavorites()

const buttonRef = ref(null)
const hovered = ref(false)

const isFav = computed(() => isFavorited(props.item.id))
const visible = computed(() => hovered.value || pressedId.value === props.item.id)
const label = computed(() => {
  if (isFav.value) return 'Remove from favorites'
  return canAddMore() ? 'Add to favorites' : 'Favorites full'
})

const toggle = () => {
  toggleFavorite(props.item)
  pressedId.value = null
}

// Phones handle the button on touchend rather than waiting for a click, so
// the row (and the card around it) can never claim the tap instead
let buttonTouch = null

const onButtonTouchStart = (e) => {
  buttonTouch = { x: e.touches[0].clientX, y: e.touches[0].clientY }
}

const onButtonTouchEnd = (e) => {
  if (!buttonTouch) return
  const touch = e.changedTouches[0]
  const moved = Math.max(Math.abs(touch.clientX - buttonTouch.x), Math.abs(touch.clientY - buttonTouch.y))
  buttonTouch = null
  if (moved > MOVE_TOLERANCE) return
  // Stops the browser's follow-up click from reaching the row
  e.preventDefault()
  if (!buttonRef.value.disabled) toggle()
}

let row = null
let pressTimer = null
let startX = 0
let startY = 0
let longPressed = false
const canHover = window.matchMedia('(hover: hover)').matches

const cancelPress = () => {
  clearTimeout(pressTimer)
  pressTimer = null
}

const onTouchStart = (e) => {
  if (buttonRef.value?.contains(e.target)) return
  longPressed = false
  startX = e.touches[0].clientX
  startY = e.touches[0].clientY
  cancelPress()
  pressTimer = setTimeout(() => {
    longPressed = true
    pressedId.value = props.item.id
    pressedRow = row
  }, LONG_PRESS_MS)
}

const onTouchMove = (e) => {
  const dx = Math.abs(e.touches[0].clientX - startX)
  const dy = Math.abs(e.touches[0].clientY - startY)
  if (Math.max(dx, dy) > MOVE_TOLERANCE) cancelPress()
}

// The click that follows a long press must not open the player/team page
const onClickCapture = (e) => {
  if (longPressed && !buttonRef.value?.contains(e.target)) {
    e.stopPropagation()
    e.preventDefault()
  }
  longPressed = false
}

// Some phones open a context menu on long press; the button replaces it
const onContextMenu = (e) => {
  if (pressTimer || longPressed) e.preventDefault()
}

const onMouseEnter = () => { if (canHover) hovered.value = true }
const onMouseLeave = () => { hovered.value = false }

onMounted(() => {
  row = buttonRef.value.parentElement
  row.classList.add('has-favorite-toggle')
  row.addEventListener('touchstart', onTouchStart, { passive: true })
  row.addEventListener('touchmove', onTouchMove, { passive: true })
  row.addEventListener('touchend', cancelPress)
  row.addEventListener('touchcancel', cancelPress)
  row.addEventListener('click', onClickCapture, true)
  row.addEventListener('contextmenu', onContextMenu)
  row.addEventListener('mouseenter', onMouseEnter)
  row.addEventListener('mouseleave', onMouseLeave)
  listenForOutsideTouch()
})

onUnmounted(() => {
  cancelPress()
  row.removeEventListener('touchstart', onTouchStart)
  row.removeEventListener('touchmove', onTouchMove)
  row.removeEventListener('touchend', cancelPress)
  row.removeEventListener('touchcancel', cancelPress)
  row.removeEventListener('click', onClickCapture, true)
  row.removeEventListener('contextmenu', onContextMenu)
  row.removeEventListener('mouseenter', onMouseEnter)
  row.removeEventListener('mouseleave', onMouseLeave)
  stopListeningForOutsideTouch()
  if (pressedRow === row) {
    pressedId.value = null
    pressedRow = null
  }
})
</script>

<style scoped>
.favorite-toggle {
  position: absolute;
  right: 0.5rem;
  top: 50%;
  transform: translateY(-50%);
  z-index: 5;
  padding: 0.35rem 0.75rem;
  font-family: var(--font-family);
  font-size: 0.8rem;
  white-space: nowrap;
  color: #000;
  background-color: var(--color-text-secondary);
  border: 2px solid var(--color-text-secondary);
  border-radius: 4px;
  cursor: pointer;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.5);
}

.favorite-toggle.favorited {
  color: var(--color-text-primary);
  background-color: var(--color-bg-card);
  border-color: var(--color-control-border);
}

.favorite-toggle:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* Long press must not select text or pop up the browser's own menu */
:global(.has-favorite-toggle) {
  -webkit-touch-callout: none;
  -webkit-user-select: none;
  user-select: none;
}
</style>
