<template>
  <span v-if="isAbsent(player)" class="absent-badge" :title="absentTitle(player)">
    {{ detailed ? `Absent · ${player.gamesMissedInARow} games` : 'Absent' }}
  </span>
</template>

<script setup>
import { isAbsent, absentTitle } from '../composables/absence'

defineProps({
  player: { type: Object, default: null },
  // Says how many games, for the player page header
  detailed: { type: Boolean, default: false }
})
</script>

<style scoped>
/* Outlined, so it reads as a status next to the name rather than a stat chip */
.absent-badge {
  flex-shrink: 0;
  padding: 0.05rem 0.4rem;
  font-size: 0.7rem;
  font-weight: bold;
  line-height: 1.4;
  text-transform: uppercase;
  letter-spacing: calc(1 * var(--tracking));
  white-space: nowrap;
  color: var(--color-muted);
  border: var(--color-border-thick) solid var(--color-control-border);
  border-radius: var(--radius-control);
}
</style>
