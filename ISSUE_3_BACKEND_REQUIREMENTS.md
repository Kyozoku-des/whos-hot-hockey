# Issue #3: Game Log Graphs - Backend Requirements

## Overview
The frontend has been implemented with game log graph visualization for both player and team pages. The backend needs to provide the necessary data endpoints to populate these graphs.

## Current Implementation Status

### Frontend ✅ Complete
- PlayerGameLogGraph component created
- TeamGameLogGraph component created
- Both integrated into their respective pages
- Chart.js library installed and configured
- Graphs display side-by-side comparison of current vs previous season

## Backend Requirements

### 1. Player Game Log Endpoint

**Endpoint:** `GET /api/players/{playerId}/game-logs`

**Query Parameters:**
- `season` (optional): Season identifier (e.g., "20242025")
  - If not provided, return current season
  - Should support returning previous season data

**Response Structure:**
```json
{
  "currentSeason": [
    {
      "gameId": "string",
      "date": "2024-10-15",
      "points": 2,
      "goals": 1,
      "assists": 1,
      "timeOnIce": "18:45"
    }
  ],
  "previousSeason": [
    {
      "gameId": "string",
      "date": "2023-10-12",
      "points": 1,
      "goals": 0,
      "assists": 1,
      "timeOnIce": "19:12"
    }
  ]
}
```

**Requirements:**
- Current season: Return games played so far (chronological order)
- Previous season: Return all 82 games (chronological order)
- Games should be sorted by date ascending (game 1 to game 82)

### 2. Team Game Log Endpoint

**Endpoint:** `GET /api/teams/{teamCode}/game-logs`

**Query Parameters:**
- `season` (optional): Season identifier (e.g., "20242025")

**Response Structure:**
```json
{
  "currentSeason": [
    {
      "gameId": "string",
      "date": "2024-10-15",
      "win": true,
      "opponent": "TOR",
      "score": "4-2",
      "homeGame": true
    }
  ],
  "previousSeason": [
    {
      "gameId": "string",
      "date": "2023-10-12",
      "win": false,
      "opponent": "MTL",
      "score": "2-3",
      "homeGame": false
    }
  ]
}
```

**Requirements:**
- Current season: Return games played so far (chronological order)
- Previous season: Return all 82 games (chronological order)
- Games should be sorted by date ascending (game 1 to game 82)
- `win` field is a boolean indicating if the team won
- Include opponent team code and final score

## Graph Specifications

### Player Graph
- **X-axis:** Game number (1, 2, 3, ..., 82)
- **Y-axis:** Points scored in that game
- **Data:** Not cumulative - shows points per individual game

### Team Graph
- **X-axis:** Game number (1, 2, 3, ..., 82)
- **Y-axis:** Binary (0 = Loss, 1 = Win)
- **Data:** Win/Loss result for each game
- **Display:** Stepped line chart showing win/loss pattern

## Data Requirements

### For Current Season
- Include only games that have been played
- Array length should match the number of games played (e.g., if 15 games played, array has 15 elements)
- Games must be in chronological order

### For Previous Season
- Include all 82 regular season games
- Array length should always be 82
- Games must be in chronological order

## Alternative Approach (If Preferred)

If returning both seasons in one response is not preferred, you can create separate endpoints:

**Option 2:**
- `GET /api/players/{playerId}/game-logs?season=current` - Returns current season only
- `GET /api/players/{playerId}/game-logs?season=previous` - Returns previous season only
- `GET /api/teams/{teamCode}/game-logs?season=current`
- `GET /api/teams/{teamCode}/game-logs?season=previous`

The frontend can make two API calls and combine the data.

## Frontend Integration Points

### PlayerPage.vue
Currently fetches data using:
```javascript
const gameLogs = ref([])  // Will hold current season data
const previousSeasonGameLogs = ref([])  // Will hold previous season data
```

### TeamPage.vue
Currently prepared to receive:
```javascript
const teamGameLogs = ref([])  // Will hold current season data
const previousSeasonTeamGameLogs = ref([])  // Will hold previous season data
```

## Next Steps for Backend Team

1. ✅ Review this requirements document
2. ⬜ Implement player game log endpoint with both current and previous season data
3. ⬜ Implement team game log endpoint with both current and previous season data
4. ⬜ Ensure data is sorted chronologically (game 1 to game 82)
5. ⬜ Test endpoints return correct data structure
6. ⬜ Coordinate with frontend team for integration testing

## Notes

- Previous season data should come from the database populated by the previous season script (from Issue #2)
- Current season data should be fetched and updated regularly by the background job (from Issue #2)
- Graph components are fully responsive and work on mobile devices
- Dark theme colors are pre-configured to match the app's design

## Questions or Clarifications?

Contact the frontend team if you need clarification on data structures or have suggestions for optimization.
