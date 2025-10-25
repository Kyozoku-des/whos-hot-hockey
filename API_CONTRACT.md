# API Contract Documentation

This document defines the REST API contract between the backend (Spring Boot) and frontend (Vue.js) for the Who's Hot NHL application.

**Version:** 1.0
**Last Updated:** 2025-10-14

## Base URL

```
http://localhost:8080/api
```

## Overview

The backend provides REST endpoints that read from a SQLite database. The database is populated by scheduled jobs that fetch data from NHL's public APIs (documented in `backend/API_REFERENCE.md`).

All responses are in JSON format. All timestamps are in ISO 8601 format.

---

## Endpoints

### 1. Team Standings

#### GET `/api/standings`

Get current season team standings.

**Response:**
```json
{
  "season": "20242025",
  "lastUpdated": "2025-10-14T12:00:00Z",
  "standings": [
    {
      "teamId": 10,
      "teamCode": "TOR",
      "teamName": "Toronto Maple Leafs",
      "wins": 5,
      "losses": 2,
      "overtimeLosses": 1,
      "points": 11,
      "gamesPlayed": 8,
      "goalsFor": 28,
      "goalsAgainst": 22,
      "goalDifferential": 6,
      "winPercentage": 0.625,
      "conferenceRank": 3,
      "divisionRank": 2
    }
  ]
}
```

---

### 2. Player Standings

#### GET `/api/players/standings`

Get current season player point leaders.

**Query Parameters:**
- `limit` (optional, integer, default: 50) - Number of players to return
- `offset` (optional, integer, default: 0) - Pagination offset

**Response:**
```json
{
  "season": "20242025",
  "lastUpdated": "2025-10-14T12:00:00Z",
  "total": 850,
  "players": [
    {
      "playerId": 8478402,
      "firstName": "Connor",
      "lastName": "McDavid",
      "teamCode": "EDM",
      "teamName": "Edmonton Oilers",
      "position": "C",
      "gamesPlayed": 8,
      "goals": 6,
      "assists": 10,
      "points": 16,
      "plusMinus": 4,
      "penaltyMinutes": 2,
      "powerPlayGoals": 2,
      "powerPlayPoints": 7,
      "gameWinningGoals": 1,
      "shotsOnGoal": 28,
      "shootingPercentage": 21.43
    }
  ]
}
```

---

### 3. Player Streaks

#### GET `/api/players/streaks`

Get current active point streaks for players.

**Query Parameters:**
- `minGames` (optional, integer, default: 3) - Minimum streak length to include
- `limit` (optional, integer, default: 50) - Number of streaks to return

**Response:**
```json
{
  "lastUpdated": "2025-10-14T12:00:00Z",
  "streaks": [
    {
      "playerId": 8478402,
      "firstName": "Connor",
      "lastName": "McDavid",
      "teamCode": "EDM",
      "teamName": "Edmonton Oilers",
      "position": "C",
      "streakLength": 8,
      "streakType": "POINT",
      "pointsDuringStreak": 16,
      "goalsDuringStreak": 6,
      "assistsDuringStreak": 10,
      "streakStartDate": "2025-10-01"
    }
  ]
}
```

---

### 4. Hot Players

#### GET `/api/players/hot`

Determine which players are "hot" based on recent performance.

**Query Parameters:**
- `games` (optional, integer, default: 5) - Number of recent games to analyze
- `limit` (optional, integer, default: 20) - Number of hot players to return
- `minGames` (optional, integer, default: 3) - Minimum games played in period

**Algorithm:**
Points per game in last N games, minimum games played threshold.

**Response:**
```json
{
  "lastUpdated": "2025-10-14T12:00:00Z",
  "periodGames": 5,
  "hotPlayers": [
    {
      "playerId": 8478402,
      "firstName": "Connor",
      "lastName": "McDavid",
      "teamCode": "EDM",
      "teamName": "Edmonton Oilers",
      "position": "C",
      "recentGamesPlayed": 5,
      "recentPoints": 10,
      "recentGoals": 4,
      "recentAssists": 6,
      "pointsPerGame": 2.0,
      "seasonPointsPerGame": 2.0,
      "hotScore": 100.0
    }
  ]
}
```

**Note:** `hotScore` is a normalized metric (0-100) where 100 = highest PPG in the analyzed period.

---

### 5. Team Win Streaks

#### GET `/api/teams/win-streaks`

Get current active win streaks for teams.

**Query Parameters:**
- `minGames` (optional, integer, default: 2) - Minimum streak length to include

**Response:**
```json
{
  "lastUpdated": "2025-10-14T12:00:00Z",
  "streaks": [
    {
      "teamId": 10,
      "teamCode": "TOR",
      "teamName": "Toronto Maple Leafs",
      "streakLength": 5,
      "streakStartDate": "2025-10-05",
      "lastGameDate": "2025-10-13",
      "goalsScoredDuringStreak": 22,
      "goalsAllowedDuringStreak": 10
    }
  ]
}
```

---

### 6. Team Losing Streaks

#### GET `/api/teams/losing-streaks`

Get current active losing streaks for teams.

**Query Parameters:**
- `minGames` (optional, integer, default: 2) - Minimum streak length to include

**Response:**
```json
{
  "lastUpdated": "2025-10-14T12:00:00Z",
  "streaks": [
    {
      "teamId": 12,
      "teamCode": "CAR",
      "teamName": "Carolina Hurricanes",
      "streakLength": 3,
      "streakStartDate": "2025-10-08",
      "lastGameDate": "2025-10-13",
      "goalsScoredDuringStreak": 5,
      "goalsAllowedDuringStreak": 12
    }
  ]
}
```

---

### 7. Player Detail

#### GET `/api/players/{playerId}`

Get detailed statistics for a specific player.

**Path Parameters:**
- `playerId` (integer, required) - NHL Player ID

**Response:**
```json
{
  "playerId": 8478402,
  "firstName": "Connor",
  "lastName": "McDavid",
  "birthDate": "1997-01-13",
  "birthCity": "Richmond Hill",
  "birthCountry": "CAN",
  "height": "73",
  "weight": 193,
  "position": "C",
  "shoots": "L",
  "teamCode": "EDM",
  "teamName": "Edmonton Oilers",
  "jerseyNumber": 97,
  "currentSeason": {
    "season": "20242025",
    "gamesPlayed": 8,
    "goals": 6,
    "assists": 10,
    "points": 16,
    "plusMinus": 4,
    "penaltyMinutes": 2,
    "powerPlayGoals": 2,
    "powerPlayPoints": 7,
    "shortHandedGoals": 0,
    "shortHandedPoints": 0,
    "gameWinningGoals": 1,
    "overtimeGoals": 0,
    "shotsOnGoal": 28,
    "shootingPercentage": 21.43,
    "faceoffWinPercentage": 52.1
  },
  "careerStats": {
    "seasons": 10,
    "gamesPlayed": 645,
    "goals": 335,
    "assists": 645,
    "points": 980,
    "pointsPerGame": 1.52
  },
  "recentGames": [
    {
      "gameId": 2024020123,
      "date": "2025-10-13",
      "opponent": "CGY",
      "isHome": true,
      "result": "W",
      "score": "5-2",
      "goals": 2,
      "assists": 1,
      "points": 3,
      "plusMinus": 2,
      "shots": 5,
      "timeOnIce": "21:34"
    }
  ]
}
```

---

### 8. Team Detail

#### GET `/api/teams/{teamCode}`

Get detailed statistics for a specific team.

**Path Parameters:**
- `teamCode` (string, required) - Three-letter team code (e.g., "TOR", "EDM")

**Response:**
```json
{
  "teamId": 10,
  "teamCode": "TOR",
  "teamName": "Toronto Maple Leafs",
  "fullName": "Toronto Maple Leafs",
  "conference": "Eastern",
  "division": "Atlantic",
  "venue": "Scotiabank Arena",
  "currentSeason": {
    "season": "20242025",
    "gamesPlayed": 8,
    "wins": 5,
    "losses": 2,
    "overtimeLosses": 1,
    "points": 11,
    "pointPercentage": 68.75,
    "goalsPerGame": 3.5,
    "goalsAgainstPerGame": 2.75,
    "powerPlayPercentage": 25.0,
    "penaltyKillPercentage": 82.5,
    "shotsPerGame": 32.5,
    "shotsAllowedPerGame": 28.0,
    "faceoffWinPercentage": 51.2
  },
  "roster": [
    {
      "playerId": 8471214,
      "firstName": "John",
      "lastName": "Tavares",
      "position": "C",
      "jerseyNumber": 91
    }
  ],
  "recentGames": [
    {
      "gameId": 2024020124,
      "date": "2025-10-13",
      "opponent": "MTL",
      "isHome": true,
      "result": "W",
      "score": "4-2",
      "goalsFor": 4,
      "goalsAgainst": 2,
      "shotsFor": 35,
      "shotsAgainst": 27
    }
  ]
}
```

---

## Error Responses

All endpoints may return the following error responses:

### 400 Bad Request
```json
{
  "error": "Bad Request",
  "message": "Invalid parameter: limit must be between 1 and 100",
  "timestamp": "2025-10-14T12:00:00Z"
}
```

### 404 Not Found
```json
{
  "error": "Not Found",
  "message": "Player with ID 9999999 not found",
  "timestamp": "2025-10-14T12:00:00Z"
}
```

### 500 Internal Server Error
```json
{
  "error": "Internal Server Error",
  "message": "An unexpected error occurred",
  "timestamp": "2025-10-14T12:00:00Z"
}
```

---

## Data Refresh Schedule

The backend should refresh data from NHL APIs on the following schedule:

- **Team Standings**: Every 30 minutes during game days, every 2 hours otherwise
- **Player Stats**: Every 30 minutes during game days, every 2 hours otherwise
- **Game Data**: Every 5 minutes when games are in progress
- **Streaks Calculation**: Recalculated on each data refresh
- **Hot Players**: Recalculated on each data refresh

---

## CORS Configuration

Backend must enable CORS for frontend development:

```
Access-Control-Allow-Origin: http://localhost:3000
Access-Control-Allow-Methods: GET, OPTIONS
Access-Control-Allow-Headers: Content-Type
```

---

## Implementation Notes

### Backend (Spring Boot)
1. Use Spring Data JPA with SQLite
2. Create scheduled jobs using `@Scheduled` for data fetching
3. Implement REST controllers for each endpoint
4. Use DTOs for API responses (separate from entities)
5. Add caching where appropriate (e.g., team/player details)
6. Implement proper error handling with `@ControllerAdvice`

### Frontend (Vue.js)
1. Create API service layer using Axios or Fetch
2. Use Vue Router for navigation between pages
3. Implement loading states while fetching data
4. Handle errors gracefully with user-friendly messages
5. Cache API responses where appropriate
6. Use environment variables for API base URL

---

### 9. Player Game Log

#### GET `/api/players/{playerId}/game-log`

Get game-by-game statistics for a specific player in a given season.

**Path Parameters:**
- `playerId` (integer, required) - NHL Player ID

**Query Parameters:**
- `season` (string, optional) - Season ID (e.g., "20252026"). Defaults to current season.

**Response:**
```json
[
  {
    "gameNumber": 1,
    "gameDate": "2025-10-10",
    "points": 2,
    "goals": 1,
    "assists": 1,
    "opponentTeamCode": "BOS",
    "homeGame": true
  },
  {
    "gameNumber": 2,
    "gameDate": "2025-10-12",
    "points": 0,
    "goals": 0,
    "assists": 0,
    "opponentTeamCode": "NYR",
    "homeGame": false
  }
]
```

**Note:** Game numbers range from 1-82 for a full regular season. Current season will only include games played so far.

---

### 10. Team Game Log

#### GET `/api/teams/{teamCode}/game-log`

Get game-by-game results for a specific team in a given season.

**Path Parameters:**
- `teamCode` (string, required) - Three-letter team code (e.g., "TOR", "EDM")

**Query Parameters:**
- `season` (string, optional) - Season ID (e.g., "20252026"). Defaults to current season.

**Response:**
```json
[
  {
    "gameNumber": 1,
    "gameDate": "2025-10-10",
    "won": true,
    "goalsFor": 5,
    "goalsAgainst": 2,
    "opponentTeamCode": "MTL",
    "homeGame": true
  },
  {
    "gameNumber": 2,
    "gameDate": "2025-10-12",
    "won": false,
    "goalsFor": 2,
    "goalsAgainst": 4,
    "opponentTeamCode": "BOS",
    "homeGame": false
  }
]
```

**Note:** Game numbers range from 1-82 for a full regular season. Current season will only include games played so far.

---

## Development Workflow

1. **Backend develops endpoints** following this contract
2. **Backend tests endpoints** using tools like Postman/curl
3. **Backend commits and pushes** to `dev-backend` branch
4. **Coordinator reviews** and merges to `master` if needed
5. **Frontend pulls changes** and implements UI
6. **Frontend tests** against backend endpoints
7. **Frontend commits and pushes** to `dev-frontend` branch

---

## Notes for Agents

### Backend Agent
- Implement endpoints in the order listed above
- Start with simple endpoints (standings, player standings) before complex ones (hot players, streaks)
- Ensure all endpoints follow this contract exactly
- Add appropriate logging for debugging
- Write unit tests for business logic (streak calculation, hot player algorithm)

### Frontend Agent
- Start with basic list views (standings, player standings)
- Implement routing between pages
- Add detail pages for players and teams
- Use the design references: `frontend/startpage-reference.png` and `frontend/player-page-reference.png`
- Implement proper error handling and loading states

---

## Version History

- **1.0** (2025-10-14): Initial API contract
- **1.1** (2025-10-25): Added player and team game log endpoints for graph visualization (Issue #3)
