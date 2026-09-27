# API Contracts: Backend Modularization

**Base URL**: `http://localhost:8080/api`
**Content-Type**: `application/json`
**CORS**: Enabled for `http://localhost:3000`

---

## 1. Search — `GET /api/search/all`

**Spec**: FR-001 | **Priority**: P1 | **Status**: Existing (already implemented)

**Query Parameters**:
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| season | string | No | Season filter (e.g., "20252026"). Defaults to active season. |

**Response** `200 OK`:
```json
[
  {
    "type": "PLAYER",
    "id": "8478402",
    "name": "Connor McDavid",
    "secondaryInfo": "EDM",
    "teamCode": "EDM",
    "imageUrl": "https://...",
    "season": "20252026"
  },
  {
    "type": "TEAM",
    "id": "TOR",
    "name": "Toronto Maple Leafs",
    "secondaryInfo": "Eastern",
    "teamCode": "TOR",
    "imageUrl": "https://...",
    "season": "20252026"
  }
]
```

---

## 2. Player Standings — `GET /api/players/standings`

**Spec**: FR-003 | **Priority**: P1 | **Frontend**: `usePlayerStats().getTopScorers()`

Returns ALL players with season stats and last-10-games points percentage for homepage display.

**Query Parameters**:
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| season | string | No | Season filter. Defaults to active season. |

**Response** `200 OK`:
```json
[
  {
    "playerId": 8478402,
    "firstName": "Connor",
    "lastName": "McDavid",
    "fullName": "Connor McDavid",
    "positionCode": "C",
    "teamCode": "EDM",
    "teamLogoUrl": "https://...",
    "headshotUrl": "https://...",
    "gamesPlayed": 72,
    "goals": 45,
    "assists": 68,
    "points": 113,
    "pointsPerGame": 1.57,
    "plusMinus": 22,
    "pointsPerLastNGames": 1.8,
    "hot": true,
    "cold": false,
    "currentPointStreak": 5,
    "currentPointlessStreak": 0
  }
]
```

---

## 3. Player Detail — `GET /api/players/{playerId}`

**Spec**: FR-005 | **Priority**: P2 | **Frontend**: `usePlayerStats().getPlayerDetails(playerId)`

Returns complete player data for the player detail page.

**Path Parameters**:
| Parameter | Type | Description |
|-----------|------|-------------|
| playerId | long | NHL player ID (e.g., 8478402) |

**Query Parameters**:
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| season | string | No | Season filter. Defaults to active season. |

**Response** `200 OK`:
```json
{
  "playerId": 8478402,
  "firstName": "Connor",
  "lastName": "McDavid",
  "fullName": "Connor McDavid",
  "positionCode": "C",
  "teamCode": "EDM",
  "teamLogoUrl": "https://...",
  "headshotUrl": "https://...",
  "gamesPlayed": 72,
  "goals": 45,
  "assists": 68,
  "points": 113,
  "pointsPerGame": 1.57,
  "plusMinus": 22,
  "pointsPerLastNGames": 1.8,
  "hot": true,
  "cold": false,
  "currentPointStreak": 5,
  "currentPointlessStreak": 0,
  "nextGame": {
    "date": "2026-03-28",
    "opponentAbbrev": "CGY",
    "homeRoadFlag": "H"
  }
}
```

**Response** `404 Not Found`:
```json
{
  "error": "Player not found",
  "playerId": 9999999
}
```

---

## 4. Player Game Log — `GET /api/players/{playerId}/game-log`

**Spec**: FR-005 | **Priority**: P2 | **Frontend**: `usePlayerStats().getPlayerGameLog(playerId, season)`

Returns game-by-game statistics for the player detail page.

**Path Parameters**:
| Parameter | Type | Description |
|-----------|------|-------------|
| playerId | long | NHL player ID |

**Query Parameters**:
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| season | string | No | Season filter. Defaults to active season. |

**Response** `200 OK`:
```json
[
  {
    "gameId": 2025020001,
    "gameDate": "2025-10-08",
    "opponentTeamCode": "VAN",
    "homeGame": true,
    "goals": 2,
    "assists": 1,
    "points": 3,
    "plusMinus": 2,
    "shots": 5,
    "timeOnIce": 1260,
    "gameWon": true,
    "gameNumber": 1
  }
]
```

---

## 5. Team Standings — `GET /api/teams/standings`

**Spec**: FR-002 | **Priority**: P1 | **Frontend**: `useTeamStats().getStandings()`

Returns ALL 32 teams with season standings and last-10-games points percentage for homepage display.

**Query Parameters**:
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| season | string | No | Season filter. Defaults to active season. |

**Response** `200 OK`:
```json
[
  {
    "teamCode": "EDM",
    "teamName": "Edmonton Oilers",
    "franchiseName": "Oilers",
    "logoUrl": "https://...",
    "gamesPlayed": 72,
    "wins": 45,
    "losses": 20,
    "overtimeLosses": 7,
    "points": 97,
    "pointPercentage": 0.674,
    "goalsFor": 245,
    "goalsAgainst": 198,
    "goalDifferential": 47,
    "conferenceName": "Western",
    "divisionName": "Pacific",
    "currentWinStreak": 3,
    "currentLossStreak": 0,
    "last10GamesWinPercentage": 0.7,
    "last10GamesPointPercentage": 0.8,
    "last10GamesPPG": 1.6,
    "hot": true,
    "cold": false,
    "pointStreak": true
  }
]
```

---

## 6. Team Detail — `GET /api/teams/{teamCode}`

**Spec**: FR-004 | **Priority**: P2 | **Frontend**: `useTeamStats().getTeamDetails(teamCode)`

Returns complete team data for the team detail page, including roster.

**Path Parameters**:
| Parameter | Type | Description |
|-----------|------|-------------|
| teamCode | string | Three-letter team code (e.g., "TOR") |

**Query Parameters**:
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| season | string | No | Season filter. Defaults to active season. |

**Response** `200 OK`:
```json
{
  "teamCode": "TOR",
  "teamName": "Toronto Maple Leafs",
  "franchiseName": "Maple Leafs",
  "logoUrl": "https://...",
  "gamesPlayed": 72,
  "wins": 42,
  "losses": 22,
  "overtimeLosses": 8,
  "points": 92,
  "pointPercentage": 0.639,
  "goalsFor": 230,
  "goalsAgainst": 210,
  "goalDifferential": 20,
  "conferenceName": "Eastern",
  "divisionName": "Atlantic",
  "currentWinStreak": 0,
  "currentLossStreak": 2,
  "last10GamesWinPercentage": 0.5,
  "last10GamesPointPercentage": 0.6,
  "last10GamesPPG": 1.2,
  "hot": false,
  "cold": false,
  "pointStreak": false,
  "roster": [
    {
      "playerId": 8478483,
      "fullName": "Auston Matthews",
      "positionCode": "C",
      "teamCode": "TOR",
      "headshotUrl": "https://..."
    }
  ]
}
```

**Response** `404 Not Found`:
```json
{
  "error": "Team not found",
  "teamCode": "ZZZ"
}
```

---

## 7. Team Game Log — `GET /api/teams/{teamCode}/game-log`

**Spec**: FR-004 | **Priority**: P2 | **Frontend**: `useTeamStats().getTeamGameLog(teamCode, season)`

Returns game-by-game results for the team detail page.

**Path Parameters**:
| Parameter | Type | Description |
|-----------|------|-------------|
| teamCode | string | Three-letter team code |

**Query Parameters**:
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| season | string | No | Season filter. Defaults to active season. |

**Response** `200 OK`:
```json
[
  {
    "gameId": 2025020001,
    "gameDate": "2025-10-08",
    "opponentTeamCode": "MTL",
    "homeGame": true,
    "goalsFor": 4,
    "goalsAgainst": 2,
    "won": true,
    "overtimeLoss": false,
    "gameType": "regular",
    "gameNumber": 1
  }
]
```

---

## Additional Endpoints (Existing Frontend Components)

These endpoints are expected by existing frontend components and should be implemented:

### `GET /api/players/point-streaks`
Returns players with active point streaks. Used by `PointStreaksTable.vue`.

### `GET /api/players/hot`
Returns players flagged as hot (strong last-10 performance). Used by `HottestPlayersTable.vue`.

### `GET /api/teams/win-streaks`
Returns teams with active win streaks. Used by `TeamWinStreaksTable.vue`.

### `GET /api/teams/loss-streaks`
Returns teams with active loss streaks. Used by `TeamWinStreaksTable.vue`.

These all follow the same response format as their parent entity (Player/Team) but filtered.

---

## Error Responses

All endpoints return standard error responses for error cases:

**400 Bad Request**: Invalid parameters
```json
{ "error": "Invalid season format", "detail": "Season must be 8 digits (e.g., 20252026)" }
```

**404 Not Found**: Resource doesn't exist
```json
{ "error": "Player not found", "playerId": 9999999 }
```

**500 Internal Server Error**: Server failure
```json
{ "error": "Internal server error" }
```

---

## Frontend Integration Notes

The Vue frontend (`useApi.js`) already defines all endpoint paths. The API base URL is configured via `VITE_API_BASE_URL` environment variable (default: `http://localhost:8080/api`). No frontend changes are needed for the API contract — the API module must match these existing paths exactly.
