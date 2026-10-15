<template>
  <!-- Hidden unless a layout turns it on: the phone home page shows it at the right of the name,
       where pointer devices get the hover tooltip instead -->
  <span v-if="game" class="next-game-badge">
    <span class="badge-content">
      <span class="label">Nxt opp:</span>
      <span class="opponent">{{ formatOpponent(game) }}</span>
      <span class="time-line">
        <span class="time">{{ formatGameTimeShort(game) }}</span>
      </span>
    </span>
  </span>
</template>

<script setup>
import { computed } from 'vue'
import { useNextGames, formatOpponent, formatGameTimeShort } from '../composables/useNextGames'

const props = defineProps({
  teamCode: { type: String, default: null }
})

const { nextGameFor } = useNextGames()
const game = computed(() => (props.teamCode ? nextGameFor(props.teamCode) : null))
</script>

<style scoped>
/* Takes only the space the name leaves over, with the label stacked above the game so the
   two lines stay within the logo's height */
.next-game-badge {
  display: none;
  flex: 1 1 0;
  min-width: 0;
  container-type: inline-size;
  font-size: 0.8rem;
  line-height: 1.2;
  white-space: nowrap;
}

/* Exactly the badge's width: sized by its content it would grow to the whole unwrapped
   game line and overflow the row by a different amount on every row */
.badge-content {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  width: 100%;
  min-width: 0;
}

/* Too narrow for even the opponent: no label left on its own either. Hidden but still
   taking up its height, so the row matches the others. */
@container (width < 3.5rem) {
  .badge-content {
    visibility: hidden;
  }
}

.label,
.time-line {
  font-size: 0.85em;
}

.label {
  color: var(--color-text-primary);
  opacity: 0.7;
}

.opponent {
  color: var(--color-text-secondary);
  font-weight: bold;
}

/* Always one line tall, so every row is the same height. A time too wide for the space
   wraps below that line and is clipped away rather than cut off mid-text. */
.time-line {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  width: 100%;
  height: 1.2em;
  overflow: hidden;
}

/* Empty first item, so the time has a line to wrap away from */
.time-line::before {
  content: '';
}

.time {
  color: var(--color-text-primary);
  opacity: 0.8;
}
</style>
