# Issue #3: Game Log Graph - Implementation Guide

**Issue URL**: https://github.com/Kyozoku-des/whos-hot-nhl/issues/3
**Status**: In Progress
**Assigned To**: Backend Agent + Frontend Agent

## Overview

Add game log graphs to both Player and Team pages showing performance progression over the season. Display current season (partial) and previous season (complete, all 82 games) side by side for comparison.

## Requirements

### Player Page Graphs
- **X-axis**: Game number (1-82)
- **Y-axis**: Points per game
- **Two graphs**:
  1. Current season: Points progression (games played so far)
  2. Previous season: Points progression (all 82 games)
- **Data**: Show points scored in each game (not cumulative)

### Team Page Graphs
- **X-axis**: Game number (1-82)
- **Y-axis**: Cumulative wins
- **Two graphs**:
  1. Current season: Win progression (games played so far)
  2. Previous season: Win progression (all 82 games)
- **Data**: Show cumulative win count after each game

## Definition of Done

✅ Graph component on PlayerPage.vue showing player game logs
✅ Graph component on TeamPage.vue showing team game logs
✅ Backend endpoints returning game log data for both seasons
✅ Previous season shows all 82 games
✅ Current season shows only games played so far
✅ Backend and frontend are aligned on data structure

---

## Backend Implementation (Backend Agent)

### What Already Exists

✅ **Entities**:
- `GameLog.java` - Player game performance data
- `TeamGame.java` - Team game results data

✅ **Repositories**:
- `GameLogRepository.java` - Query methods for player game logs
- `TeamGameRepository.java` - Query methods for team games

✅ **Data Synchronization**:
- Game logs are already being fetched and stored during data sync
- Previous season data can be populated using `--populate-previous-season=true`

### What Needs to be Built

#### 1. New REST Endpoints

**PlayerController.java** - Add endpoint to get player game logs:

```java
/**
 * Get game log for a specific player and season.
 * Returns chronological list of games with points per game.
 *
 * @param playerId Player ID
 * @param season Season ID (optional, defaults to current season)
 * @return List of game logs ordered by date ascending
 */
@GetMapping("/{playerId}/game-log")
public ResponseEntity<List<GameLogDTO>> getPlayerGameLog(
    @PathVariable Long playerId,
    @RequestParam(required = false) String season
) {
    // Implementation
}
```

**TeamController.java** - Add endpoint to get team game logs:

```java
/**
 * Get game log for a specific team and season.
 * Returns chronological list of games with cumulative wins.
 *
 * @param teamCode Team code (e.g., "TOR")
 * @param season Season ID (optional, defaults to current season)
 * @return List of team games ordered by date ascending
 */
@GetMapping("/{teamCode}/game-log")
public ResponseEntity<List<TeamGameLogDTO>> getTeamGameLog(
    @PathVariable String teamCode,
    @RequestParam(required = false) String season
) {
    // Implementation
}
```

#### 2. DTOs (Data Transfer Objects)

Create DTOs to structure the response:

**GameLogDTO.java**:
```java
public class GameLogDTO {
    private Integer gameNumber;      // 1-82
    private String gameDate;          // ISO format
    private String opponentTeamCode;
    private Boolean homeGame;
    private Integer goals;
    private Integer assists;
    private Integer points;           // Goals + Assists
    private Boolean gameWon;
}
```

**TeamGameLogDTO.java**:
```java
public class TeamGameLogDTO {
    private Integer gameNumber;       // 1-82
    private String gameDate;           // ISO format
    private String opponentTeamCode;
    private Boolean homeGame;
    private Boolean won;
    private Integer cumulativeWins;    // Running total of wins
    private Integer goalsFor;
    private Integer goalsAgainst;
}
```

#### 3. Service Layer Methods

**StatisticsService.java** - Add methods:

```java
/**
 * Get player game log for a season, ordered chronologically.
 * Calculates game numbers (1-82).
 */
public List<GameLogDTO> getPlayerGameLog(Long playerId, String seasonId) {
    List<GameLog> gameLogs = gameLogRepository.findByPlayerIdOrderByGameDateAsc(playerId);
    // Convert to DTO and add game numbers
    return convertToGameLogDTOs(gameLogs);
}

/**
 * Get team game log for a season with cumulative wins.
 * Calculates game numbers (1-82) and running win totals.
 */
public List<TeamGameLogDTO> getTeamGameLog(String teamCode, String seasonId) {
    List<TeamGame> teamGames = teamGameRepository.findByTeamCodeOrderByGameDateAsc(teamCode);
    // Convert to DTO, add game numbers, and calculate cumulative wins
    return convertToTeamGameLogDTOs(teamGames);
}
```

#### 4. Repository Updates (if needed)

Add ascending order methods to repositories:

**GameLogRepository.java**:
```java
List<GameLog> findByPlayerIdOrderByGameDateAsc(Long playerId);
```

**TeamGameRepository.java**:
```java
List<TeamGame> findByTeamCodeOrderByGameDateAsc(String teamCode);
```

### Testing Checklist

- [ ] Endpoint returns data for current season
- [ ] Endpoint returns data for previous season (20242025 format)
- [ ] Game numbers are sequential (1-82)
- [ ] Cumulative wins calculated correctly for teams
- [ ] Empty list returned gracefully if no data
- [ ] Data ordered chronologically (oldest game first)

### API Response Examples

**GET /api/players/8478402/game-log?season=20242025**
```json
[
  {
    "gameNumber": 1,
    "gameDate": "2024-10-10",
    "opponentTeamCode": "MTL",
    "homeGame": true,
    "goals": 1,
    "assists": 2,
    "points": 3,
    "gameWon": true
  },
  {
    "gameNumber": 2,
    "gameDate": "2024-10-12",
    "opponentTeamCode": "BOS",
    "homeGame": false,
    "goals": 0,
    "assists": 1,
    "points": 1,
    "gameWon": false
  }
  // ... up to game 82
]
```

**GET /api/teams/TOR/game-log?season=20242025**
```json
[
  {
    "gameNumber": 1,
    "gameDate": "2024-10-10",
    "opponentTeamCode": "MTL",
    "homeGame": true,
    "won": true,
    "cumulativeWins": 1,
    "goalsFor": 4,
    "goalsAgainst": 2
  },
  {
    "gameNumber": 2,
    "gameDate": "2024-10-12",
    "opponentTeamCode": "BOS",
    "homeGame": false,
    "won": false,
    "cumulativeWins": 1,
    "goalsFor": 2,
    "goalsAgainst": 3
  }
  // ... up to game 82
]
```

---

## Frontend Implementation (Frontend Agent)

### What Already Exists

✅ **Pages**:
- `PlayerPage.vue` - Player detail page
- `TeamPage.vue` - Team detail page

✅ **API Integration**:
- `useApi.js` composable for API calls

### What Needs to be Built

#### 1. Install Chart Library

Add Chart.js to the project:

```bash
npm install chart.js vue-chartjs
```

#### 2. Create Chart Components

**PlayerGameLogChart.vue**:
```vue
<template>
  <div class="game-log-chart-container">
    <h3>Game Log - Points Per Game</h3>
    <div class="charts-grid">
      <div class="chart-section">
        <h4>Current Season ({{ currentSeasonGames }} games)</h4>
        <Line :data="currentSeasonChartData" :options="chartOptions" />
      </div>
      <div class="chart-section">
        <h4>Previous Season (82 games)</h4>
        <Line :data="previousSeasonChartData" :options="chartOptions" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { Line } from 'vue-chartjs'
import { Chart as ChartJS, CategoryScale, LinearScale, PointElement, LineElement, Title, Tooltip, Legend } from 'chart.js'

ChartJS.register(CategoryScale, LinearScale, PointElement, LineElement, Title, Tooltip, Legend)

const props = defineProps({
  playerId: {
    type: Number,
    required: true
  }
})

const currentSeasonData = ref([])
const previousSeasonData = ref([])

const chartOptions = {
  responsive: true,
  maintainAspectRatio: true,
  scales: {
    x: {
      title: {
        display: true,
        text: 'Game Number'
      }
    },
    y: {
      title: {
        display: true,
        text: 'Points'
      },
      beginAtZero: true
    }
  }
}

// Fetch data and prepare chart datasets
onMounted(async () => {
  // Fetch current season
  const currentResponse = await fetch(`/api/players/${props.playerId}/game-log`)
  currentSeasonData.value = await currentResponse.json()

  // Fetch previous season
  const previousSeason = calculatePreviousSeason()
  const previousResponse = await fetch(`/api/players/${props.playerId}/game-log?season=${previousSeason}`)
  previousSeasonData.value = await previousResponse.json()
})

const currentSeasonChartData = computed(() => ({
  labels: currentSeasonData.value.map(g => g.gameNumber),
  datasets: [{
    label: 'Points',
    data: currentSeasonData.value.map(g => g.points),
    borderColor: 'rgb(75, 192, 192)',
    backgroundColor: 'rgba(75, 192, 192, 0.2)',
    tension: 0.1
  }]
}))

const previousSeasonChartData = computed(() => ({
  labels: previousSeasonData.value.map(g => g.gameNumber),
  datasets: [{
    label: 'Points',
    data: previousSeasonData.value.map(g => g.points),
    borderColor: 'rgb(153, 102, 255)',
    backgroundColor: 'rgba(153, 102, 255, 0.2)',
    tension: 0.1
  }]
}))
</script>

<style scoped>
.game-log-chart-container {
  margin: 2rem 0;
  padding: 1.5rem;
  background: var(--color-bg-card);
  border-radius: 8px;
}

.charts-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 2rem;
  margin-top: 1rem;
}

.chart-section h4 {
  text-align: center;
  margin-bottom: 1rem;
  color: var(--color-text-primary);
}

@media (max-width: 768px) {
  .charts-grid {
    grid-template-columns: 1fr;
  }
}
</style>
```

**TeamGameLogChart.vue**:
```vue
<template>
  <div class="game-log-chart-container">
    <h3>Game Log - Cumulative Wins</h3>
    <div class="charts-grid">
      <div class="chart-section">
        <h4>Current Season ({{ currentSeasonGames }} games)</h4>
        <Line :data="currentSeasonChartData" :options="chartOptions" />
      </div>
      <div class="chart-section">
        <h4>Previous Season (82 games)</h4>
        <Line :data="previousSeasonChartData" :options="chartOptions" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { Line } from 'vue-chartjs'
import { Chart as ChartJS, CategoryScale, LinearScale, PointElement, LineElement, Title, Tooltip, Legend } from 'chart.js'

ChartJS.register(CategoryScale, LinearScale, PointElement, LineElement, Title, Tooltip, Legend)

const props = defineProps({
  teamCode: {
    type: String,
    required: true
  }
})

const currentSeasonData = ref([])
const previousSeasonData = ref([])

const chartOptions = {
  responsive: true,
  maintainAspectRatio: true,
  scales: {
    x: {
      title: {
        display: true,
        text: 'Game Number'
      }
    },
    y: {
      title: {
        display: true,
        text: 'Cumulative Wins'
      },
      beginAtZero: true
    }
  }
}

// Fetch data and prepare chart datasets
onMounted(async () => {
  // Fetch current season
  const currentResponse = await fetch(`/api/teams/${props.teamCode}/game-log`)
  currentSeasonData.value = await currentResponse.json()

  // Fetch previous season
  const previousSeason = calculatePreviousSeason()
  const previousResponse = await fetch(`/api/teams/${props.teamCode}/game-log?season=${previousSeason}`)
  previousSeasonData.value = await previousResponse.json()
})

const currentSeasonChartData = computed(() => ({
  labels: currentSeasonData.value.map(g => g.gameNumber),
  datasets: [{
    label: 'Cumulative Wins',
    data: currentSeasonData.value.map(g => g.cumulativeWins),
    borderColor: 'rgb(75, 192, 192)',
    backgroundColor: 'rgba(75, 192, 192, 0.2)',
    tension: 0.1
  }]
}))

const previousSeasonChartData = computed(() => ({
  labels: previousSeasonData.value.map(g => g.gameNumber),
  datasets: [{
    label: 'Cumulative Wins',
    data: previousSeasonData.value.map(g => g.cumulativeWins),
    borderColor: 'rgb(153, 102, 255)',
    backgroundColor: 'rgba(153, 102, 255, 0.2)',
    tension: 0.1
  }]
}))
</script>

<style scoped>
/* Same styles as PlayerGameLogChart.vue */
</style>
```

#### 3. Integrate Charts into Pages

**PlayerPage.vue** - Add chart component:
```vue
<template>
  <div class="player-page">
    <!-- Existing player info -->

    <!-- NEW: Add game log chart -->
    <PlayerGameLogChart :playerId="playerId" />

    <!-- Existing stats tables -->
  </div>
</template>

<script setup>
import PlayerGameLogChart from '../components/PlayerGameLogChart.vue'
// ... existing imports
</script>
```

**TeamPage.vue** - Add chart component:
```vue
<template>
  <div class="team-page">
    <!-- Existing team info -->

    <!-- NEW: Add game log chart -->
    <TeamGameLogChart :teamCode="teamCode" />

    <!-- Existing stats tables -->
  </div>
</template>

<script setup>
import TeamGameLogChart from '../components/TeamGameLogChart.vue'
// ... existing imports
</script>
```

#### 4. Helper Functions

Add season calculation utility:

```javascript
/**
 * Calculate previous season ID from current date.
 * Example: If current season is 20252026, returns "20242025"
 */
function calculatePreviousSeason() {
  const currentYear = new Date().getFullYear()
  const currentMonth = new Date().getMonth() + 1

  let seasonStartYear
  if (currentMonth >= 10) {
    seasonStartYear = currentYear
  } else {
    seasonStartYear = currentYear - 1
  }

  const previousStartYear = seasonStartYear - 1
  const previousEndYear = seasonStartYear

  return `${previousStartYear}${previousEndYear}`
}
```

### Testing Checklist

- [ ] Charts display on PlayerPage.vue
- [ ] Charts display on TeamPage.vue
- [ ] Current season shows correct number of games
- [ ] Previous season shows all 82 games
- [ ] Charts are responsive on mobile
- [ ] Loading states handled gracefully
- [ ] Error states handled gracefully
- [ ] Chart colors are accessible

---

## Coordination Notes

### Data Alignment

**Backend provides**:
- Game numbers (1-82) calculated from date order
- Chronological game data (oldest first)
- Cumulative wins for teams (running total)
- Points per game for players

**Frontend expects**:
- Array of game objects with gameNumber field
- Chronological order (game 1 first)
- cumulativeWins field for teams
- points field for players

### Season Management

- Current season ID: Retrieved from `/api/data/current-season`
- Previous season ID: Calculate by subtracting 1 year
- Format: `YYYYYYYY` (e.g., "20242025")

### Testing Together

Once both agents complete their implementation:
1. Backend agent: Verify endpoints return correct data
2. Frontend agent: Verify charts render with mock data
3. Coordination: Test integration end-to-end
4. Verify previous season data exists (run `--populate-previous-season=true` if needed)

---

## Implementation Order

1. **Backend Agent**: Implement endpoints and DTOs first
2. **Frontend Agent**: Can start with mock data, integrate real API later
3. **Coordination**: Test integration together
4. **Backend Agent**: Ensure previous season data is populated

---

## Questions or Blockers?

If you encounter any issues or have questions:
- Backend Agent: Comment in your branch commits
- Frontend Agent: Comment in your branch commits
- Coordination Agent: Will review and help resolve blockers

---

**Start Date**: 2025-10-25
**Target Completion**: When both agents signal completion
**Coordinator**: Available to help resolve conflicts and test integration
