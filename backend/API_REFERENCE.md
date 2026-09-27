# Who's Hot NHL - API Reference

Base URL: `http://localhost:8080`

All endpoints return JSON. The `season` query parameter is optional on every endpoint and defaults to the active NHL season when omitted. Format: `YYYYYYYY` (e.g., `20242025`).

---

## Search

### GET /api/search/all

Returns the full search index for one season: every team followed by every player.

- **Query params**: `season` (optional)
- **Description**: Clients fetch this once, cache it, and filter locally for autocomplete — there is no server-side query parameter. `season` on the envelope identifies which season the index belongs to, so a cached copy can be invalidated when the season rolls over; it is deliberately not repeated on each result.
- **Size**: ~800 entries, ~146 KB raw / ~16 KB gzipped. Responses are gzipped (`server.compression.enabled`).

**Example response:**
```json
{
  "season": "20252026",
  "count": 2,
  "results": [
    {
      "type": "TEAM",
      "id": "EDM",
      "name": "Edmonton Oilers",
      "secondaryInfo": "Pacific",
      "teamCode": "EDM",
      "imageUrl": "https://assets.nhle.com/logos/nhl/svg/EDM_light.svg"
    },
    {
      "type": "PLAYER",
      "id": "8478402",
      "name": "Connor McDavid",
      "secondaryInfo": "C",
      "teamCode": "EDM",
      "imageUrl": "https://assets.nhle.com/mugs/nhl/20252026/EDM/8478402.png"
    }
  ]
}
```

---

## Players

### GET /api/players/standings

All players with season stats, ordered by points.

- **Query params**: `season` (optional)

**Example response:**
```json
[
  {
    "playerId": 8478402,
    "name": "Connor McDavid",
    "teamCode": "EDM",
    "position": "C",
    "gamesPlayed": 60,
    "goals": 30,
    "assists": 50,
    "points": 80
  }
]
```

### GET /api/players/point-streaks

Players with active point streaks.

- **Query params**: `season` (optional)
- **Description**: Returns players who have recorded at least one point in consecutive recent games, sorted by streak length descending.

**Example response:**
```json
[
  {
    "playerId": 8478402,
    "name": "Connor McDavid",
    "teamCode": "EDM",
    "streakLength": 12
  }
]
```

### GET /api/players/hot

Players with strong recent performance ("hot" players).

- **Query params**: `season` (optional)
- **Description**: Returns players ranked by their points-per-game average over recent games.

**Example response:**
```json
[
  {
    "playerId": 8478402,
    "name": "Connor McDavid",
    "teamCode": "EDM",
    "hotRating": 2.1
  }
]
```

### GET /api/players/{playerId}

Player detail with identity info and season stats.

- **Path params**: `playerId` (int) - NHL player ID
- **Query params**: `season` (optional)

**Example response:**
```json
{
  "playerId": 8478402,
  "name": "Connor McDavid",
  "teamCode": "EDM",
  "position": "C",
  "gamesPlayed": 60,
  "goals": 30,
  "assists": 50,
  "points": 80
}
```

### GET /api/players/{playerId}/game-log

Player game-by-game log for a season, ordered by `gameNumber` ascending.

- **Path params**: `playerId` (int) - NHL player ID
- **Query params**: `season` (optional)

**Season scoping**: `season` (e.g. `20242025`) selects any season present in the database, including
past seasons loaded with the backfill (see `README.md`, "Backfilling a past season"). Omitted, it
defaults to the active season. A season that was never loaded, or one the player/team has no games
in, returns `200` with an empty list `[]` — never an error or zeroed rows.

**Example response:**
```json
[
  {
    "gameId": 2024020015,
    "gameDate": "2024-10-10",
    "opponentTeamCode": "TOR",
    "homeGame": true,
    "goals": 1,
    "assists": 2,
    "points": 3,
    "plusMinus": 2,
    "shots": 4,
    "timeOnIce": 1265,
    "gameWon": true,
    "gameNumber": 1
  }
]
```

---

## Teams

### GET /api/teams/standings

All 32 teams with standings, ordered by points.

- **Query params**: `season` (optional)

**Example response:**
```json
[
  {
    "teamCode": "WPG",
    "name": "Winnipeg Jets",
    "gamesPlayed": 60,
    "wins": 40,
    "losses": 15,
    "otLosses": 5,
    "points": 85
  }
]
```

### GET /api/teams/win-streaks

Teams with active win streaks.

- **Query params**: `season` (optional)

**Example response:**
```json
[
  {
    "teamCode": "WPG",
    "name": "Winnipeg Jets",
    "streakLength": 7
  }
]
```

### GET /api/teams/loss-streaks

Teams with active loss streaks.

- **Query params**: `season` (optional)

**Example response:**
```json
[
  {
    "teamCode": "SJS",
    "name": "San Jose Sharks",
    "streakLength": 5
  }
]
```

### GET /api/teams/{teamCode}

Team detail with roster.

- **Path params**: `teamCode` (string) - Three-letter team code (e.g., `EDM`, `TOR`)
- **Query params**: `season` (optional)

**Example response:**
```json
{
  "teamCode": "EDM",
  "name": "Edmonton Oilers",
  "gamesPlayed": 60,
  "wins": 35,
  "losses": 18,
  "otLosses": 7,
  "points": 77,
  "roster": [
    { "playerId": 8478402, "name": "Connor McDavid", "position": "C" }
  ]
}
```

### GET /api/teams/{teamCode}/game-log

Team game results for a season, ordered by `gameDate` descending.

- **Path params**: `teamCode` (string) - Three-letter team code
- **Query params**: `season` (optional)

**Season scoping**: `season` (e.g. `20242025`) selects any season present in the database, including
past seasons loaded with the backfill (see `README.md`, "Backfilling a past season"). Omitted, it
defaults to the active season. A season that was never loaded, or one the player/team has no games
in, returns `200` with an empty list `[]` — never an error or zeroed rows.

**Example response:**
```json
[
  {
    "gameId": 2024020015,
    "gameDate": "2024-10-10",
    "opponentTeamCode": "TOR",
    "homeGame": true,
    "goalsFor": 4,
    "goalsAgainst": 2,
    "won": true,
    "overtimeLoss": false,
    "gameType": "2",
    "gameNumber": 1
  }
]
```

---

## Error Responses

All endpoints may return the following errors:

| Status | Meaning | Example |
|--------|---------|---------|
| 400 | Bad request (invalid parameters) | `{ "error": "Invalid season format" }` |
| 404 | Resource not found | `{ "error": "Player not found" }` |
| 500 | Internal server error | `{ "error": "Internal server error" }` |
