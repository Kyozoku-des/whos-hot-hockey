<template>
  <div class="favorites-list">
    <div v-if="favorites.length === 0" class="empty-state">
      <div class="empty-icon">⭐</div>
      <h3>No favorites yet</h3>
      <p>Click the ★ icon on any player or team to add them here!</p>
    </div>

    <div v-else class="favorites-grid">
      <!-- Player Favorites -->
      <div
        v-for="favorite in playerFavorites"
        :key="favorite.id"
        class="player-item"
        @click="goToPlayer(favorite.id)"
      >
        <button
          class="remove-btn"
          @click.stop="handleRemove(favorite)"
          title="Remove from favorites"
        >
          ✕
        </button>
        <div class="player-main">
          <img
            v-if="favorite.imageUrl"
            :src="favorite.imageUrl"
            :alt="favorite.name"
            class="player-headshot"
            @error="handleImageError"
          />
          <div v-else class="image-placeholder">👤</div>
          <span class="player-name">{{ favorite.name }}</span>
        </div>
        <span class="player-info">
          <span class="info-item">{{ favorite.secondaryInfo }}</span>
          <span class="info-badge">PLAYER</span>
        </span>
      </div>

      <!-- Team Favorites -->
      <div
        v-for="favorite in teamFavorites"
        :key="favorite.id"
        class="team-item"
        @click="goToTeam(favorite.id)"
      >
        <button
          class="remove-btn"
          @click.stop="handleRemove(favorite)"
          title="Remove from favorites"
        >
          ✕
        </button>
        <div class="team-main">
          <TeamLogo
            v-if="favorite.imageUrl"
            :logoUrl="favorite.imageUrl"
            :teamCode="favorite.id"
            :alt="favorite.name"
            size="small"
          />
          <div v-else class="image-placeholder">🏒</div>
          <span class="team-name">{{ favorite.name }}</span>
        </div>
        <span class="team-info">
          <span class="info-item">{{ favorite.secondaryInfo }}</span>
          <span class="info-badge">TEAM</span>
        </span>
      </div>
    </div>

    <div v-if="favorites.length > 0" class="favorites-count">
      {{ favorites.length }} / {{ maxFavorites }} favorites
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useFavorites } from '../composables/useFavorites'
import TeamLogo from './TeamLogo.vue'

const router = useRouter()
const { favorites, removeFavorite } = useFavorites()

const maxFavorites = 10

// Separate player and team favorites
const playerFavorites = computed(() => {
  return favorites.value.filter(fav => fav.type === 'PLAYER')
})

const teamFavorites = computed(() => {
  return favorites.value.filter(fav => fav.type === 'TEAM')
})

const handleRemove = (favorite) => {
  removeFavorite(favorite.id)
}

const goToPlayer = (playerId) => {
  router.push(`/player/${playerId}`)
}

const goToTeam = (teamCode) => {
  router.push(`/team/${teamCode}`)
}

const handleImageError = (event) => {
  event.target.style.display = 'none'
}
</script>

<style scoped>
.favorites-list {
  width: 100%;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 3rem 2rem;
  text-align: center;
}

.empty-icon {
  font-size: 4rem;
  margin-bottom: 1rem;
  opacity: 0.3;
}

.empty-state h3 {
  font-size: 1.5rem;
  font-weight: 600;
  color: var(--color-text-primary);
  margin: 0 0 0.5rem 0;
}

.empty-state p {
  font-size: 1rem;
  color: var(--color-text-secondary);
  margin: 0;
}

.favorites-grid {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  padding-right: 1.5rem;
  padding-top: 2.5rem;
}

/* Player Item Styles (matching TopPointsTable) */
.player-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.75rem 1rem;
  background-color: rgba(255, 255, 255, 0.05);
  border: 2px solid var(--color-border);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
  position: relative;
}

.player-item:hover {
  background-color: rgba(255, 255, 255, 0.1);
  transform: translateX(5px);
  z-index: 10;
}

.player-main {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  flex: 1;
  padding-left: 2.5rem;
}

.player-headshot {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  object-fit: cover;
  border: 2px solid var(--color-border);
}

.player-name {
  color: var(--color-text-secondary);
  font-size: 1rem;
  font-weight: bold;
  flex: 1;
}

.player-info {
  display: flex;
  gap: 1rem;
  align-items: center;
}

/* Team Item Styles (matching TeamStandingsTable) */
.team-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.75rem 1rem;
  background-color: rgba(255, 255, 255, 0.05);
  border: 2px solid var(--color-border);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
  position: relative;
}

.team-item:hover {
  background-color: rgba(255, 255, 255, 0.1);
  transform: translateX(5px);
  z-index: 10;
}

.team-main {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  flex: 1;
  padding-left: 2.5rem;
}

.team-name {
  color: var(--color-text-secondary);
  font-size: 1rem;
  font-weight: bold;
  flex: 1;
}

.team-info {
  display: flex;
  gap: 1rem;
  align-items: center;
}

/* Shared Styles */
.image-placeholder {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background-color: rgba(255, 255, 255, 0.05);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.5rem;
  border: 2px solid var(--color-border);
}

.info-item {
  color: var(--color-text-primary);
  font-size: 0.9rem;
  font-weight: normal;
  white-space: nowrap;
}

.info-badge {
  color: var(--color-text-primary);
  font-size: 0.75rem;
  font-weight: 600;
  padding: 0.25rem 0.5rem;
  background-color: rgba(255, 170, 0, 0.2);
  border-radius: 4px;
  text-transform: uppercase;
}

.remove-btn {
  position: absolute;
  left: 0.5rem;
  top: 50%;
  transform: translateY(-50%);
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  color: #ff6b6b;
  font-size: 1.2rem;
  font-weight: bold;
  cursor: pointer;
  transition: all 0.2s ease;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 5;
}

.remove-btn:hover {
  color: #ff5252;
  filter: drop-shadow(0 0 8px rgba(255, 107, 107, 0.8));
  text-shadow: 0 0 10px rgba(255, 107, 107, 0.6);
}

.favorites-count {
  text-align: center;
  padding: 1rem;
  font-size: 0.9rem;
  color: var(--color-text-secondary);
  font-weight: 600;
}
</style>
