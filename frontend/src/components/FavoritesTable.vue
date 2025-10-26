<template>
  <div class="favorites-list">
    <div v-if="favorites.length === 0" class="empty-state">
      <div class="empty-icon">⭐</div>
      <h3>No favorites yet</h3>
      <p>Click the ★ icon on any player or team to add them here!</p>
    </div>

    <div v-else class="favorites-grid">
      <div
        v-for="favorite in favorites"
        :key="favorite.id"
        class="favorite-item"
        @click="goToItem(favorite)"
      >
        <button
          class="remove-btn"
          @click.stop="handleRemove(favorite)"
          title="Remove from favorites"
        >
          ✕
        </button>

        <div class="item-image">
          <img
            v-if="favorite.imageUrl"
            :src="favorite.imageUrl"
            :alt="favorite.name"
            @error="handleImageError"
          />
          <div v-else class="image-placeholder">
            {{ favorite.type === 'PLAYER' ? '👤' : '🏒' }}
          </div>
        </div>

        <div class="item-info">
          <div class="item-name">{{ favorite.name }}</div>
          <div class="item-secondary">{{ favorite.secondaryInfo }}</div>
          <div class="item-badge" :class="`badge-${favorite.type.toLowerCase()}`">
            {{ favorite.type }}
          </div>
        </div>

        <div class="favorite-star">★</div>
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

const router = useRouter()
const { favorites, removeFavorite } = useFavorites()

const maxFavorites = 10

const handleRemove = (favorite) => {
  removeFavorite(favorite.id)
}

const goToItem = (favorite) => {
  const path = favorite.type === 'PLAYER'
    ? `/player/${favorite.id}`
    : `/team/${favorite.id}`
  router.push(path)
}

const handleImageError = (event) => {
  event.target.style.display = 'none'
}
</script>

<style scoped>
.favorites-list {
  width: 100%;
  min-height: 200px;
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
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 1rem;
  padding-right: 1.5rem;
}

.favorite-item {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 1.5rem 1rem;
  background-color: rgba(255, 255, 255, 0.05);
  border: 2px solid var(--color-border);
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.favorite-item:hover {
  background-color: rgba(255, 170, 0, 0.1);
  border-color: #FFAA00;
  transform: translateY(-4px);
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.3);
}

.remove-btn {
  position: absolute;
  top: 0.5rem;
  right: 0.5rem;
  width: 28px;
  height: 28px;
  border: none;
  background-color: rgba(255, 0, 0, 0.2);
  color: #ff6b6b;
  font-size: 1rem;
  font-weight: bold;
  border-radius: 50%;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
  opacity: 0.7;
}

.remove-btn:hover {
  opacity: 1;
  background-color: rgba(255, 0, 0, 0.4);
  transform: scale(1.1);
}

.item-image {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  overflow: hidden;
  margin-bottom: 1rem;
  background-color: rgba(255, 255, 255, 0.05);
  display: flex;
  align-items: center;
  justify-content: center;
  border: 2px solid var(--color-border);
}

.item-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.image-placeholder {
  font-size: 2.5rem;
}

.item-info {
  text-align: center;
  width: 100%;
}

.item-name {
  font-weight: 600;
  color: var(--color-text-primary);
  font-size: 1rem;
  margin-bottom: 0.25rem;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.item-secondary {
  font-size: 0.85rem;
  color: var(--color-text-secondary);
  margin-bottom: 0.5rem;
}

.item-badge {
  display: inline-block;
  padding: 0.25rem 0.5rem;
  border-radius: 4px;
  font-size: 0.7rem;
  font-weight: 600;
  text-transform: uppercase;
}

.badge-player {
  background-color: rgba(59, 130, 246, 0.2);
  color: #60a5fa;
}

.badge-team {
  background-color: rgba(239, 68, 68, 0.2);
  color: #f87171;
}

.favorite-star {
  position: absolute;
  bottom: 0.5rem;
  right: 0.5rem;
  font-size: 1.5rem;
  color: #FFD700;
  opacity: 0.8;
}

.favorites-count {
  text-align: center;
  padding: 1rem;
  font-size: 0.9rem;
  color: var(--color-text-secondary);
  font-weight: 600;
}

@media (max-width: 768px) {
  .favorites-grid {
    grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
    gap: 0.75rem;
  }

  .favorite-item {
    padding: 1rem 0.75rem;
  }

  .item-image {
    width: 60px;
    height: 60px;
  }

  .item-name {
    font-size: 0.9rem;
  }
}
</style>
