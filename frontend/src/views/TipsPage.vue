<template>
  <InfoPage title="Tips">
    <section class="info-section">
      <h2>Search and filter</h2>
      <p>
        Typing in the search bar filters every table on the home page as you type. A player
        name shows that player; a team name (like "Bruins" or "Toronto") shows that team and
        all of its players.
      </p>
      <p>
        Press Enter, or tap "Press Enter to filter", to keep the filter and close the dropdown.
        Pick a result with the arrow keys or a click to open its page instead. Escape clears
        the search.
      </p>
    </section>

    <section class="info-section">
      <h2>Customize your view</h2>
      <p>
        On a computer, drag a card onto another to swap their places. On a phone, swipe between
        cards and pick their order in <router-link to="/settings" class="inline-link">Settings</router-link>.
      </p>
      <p>Click a card to expand it and see more stats. Settings also has your time zone for game times.</p>
    </section>

    <section class="info-section">
      <h2>Favorites</h2>
      <p>
        Hover a row (or press and hold it on a phone) to add that player or team to your
        favorites, up to 10. They show up in their own Favorites card on the home page, and
        player and team pages have a favorite button too. Hovering a row on a computer also
        shows that team's next game.
      </p>
    </section>

    <section class="info-section">
      <h2>Graphs</h2>
      <p>
        Player and team pages graph points over the season. Switch between the season total and
        points per game; the red cross marks the total they're on pace for.
      </p>
    </section>

    <section class="info-section">
      <h2>Stats explained</h2>
      <dl class="glossary">
        <template v-for="stat in STATS" :key="stat.abbr">
          <dt>{{ stat.abbr }}</dt>
          <dd>{{ stat.meaning }}</dd>
        </template>
      </dl>
      <p class="info-note">
        "Last 10 games" cards rank by the last 10 games played (or all games, if fewer).
        Stats refresh at least hourly, and more often while games are on.
      </p>
    </section>
  </InfoPage>
</template>

<script setup>
import InfoPage from '../components/InfoPage.vue'

const STATS = [
  { abbr: 'G', meaning: 'Goals' },
  { abbr: 'A', meaning: 'Assists' },
  { abbr: 'P / PTS', meaning: 'Points: goals + assists for players, standings points for teams' },
  { abbr: 'GP', meaning: 'Games played' },
  { abbr: 'PPG', meaning: 'Points per game' },
  { abbr: 'W / L / OTL', meaning: 'Wins, losses and overtime losses' },
  { abbr: 'P%', meaning: 'Share of available standings points earned' },
  { abbr: 'GF / GA', meaning: 'Goals for and goals against' },
  { abbr: 'DIFF', meaning: 'Goal differential (GF - GA)' },
  { abbr: 'Games', meaning: 'Current streak: games in a row with a point (players) or a win (teams)' }
]
</script>

<style scoped>
.glossary {
  display: grid;
  grid-template-columns: max-content 1fr;
  gap: 0.5rem 1rem;
  font-size: 0.85rem;
  line-height: 1.5;
  margin-bottom: 1rem;
}

.glossary dt {
  color: var(--color-text-secondary);
}

.inline-link {
  color: var(--color-text-secondary);
  text-decoration: underline;
  text-underline-offset: 3px;
}
</style>
