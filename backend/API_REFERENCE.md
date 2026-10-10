# Who's Hot NHL - API Reference

Base URL: `http://localhost:8080`

All endpoints are `GET` and return JSON. The `season` query parameter is optional on every endpoint and defaults to the active NHL season when omitted. Format: `YYYYYYYY` (e.g., `20242025`).

Numeric fields are `null` when the data job has not computed them yet.

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
      "secondaryInfo": "EDM",
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

The three list endpoints below return the same player row:

```json
{
  "playerId": 8478402,
  "firstName": "Connor",
  "lastName": "McDavid",
  "fullName": "Connor McDavid",
  "positionCode": "C",
  "teamCode": "EDM",
  "teamLogoUrl": "https://assets.nhle.com/logos/nhl/svg/EDM_light.svg",
  "headshotUrl": "https://assets.nhle.com/mugs/nhl/20252026/EDM/8478402.png",
  "gamesPlayed": 60,
  "goals": 30,
  "assists": 50,
  "points": 80,
  "pointsPerGame": 1.33,
  "plusMinus": 12,
  "pointsPerLastNGames": 2.1,
  "currentPointStreak": 12,
  "currentPointlessStreak": 0
}
```

- `pointsPerLastNGames` — points per game over the player's last 10 games (fewer if they have played fewer).
- `currentPointStreak` / `currentPointlessStreak` — consecutive most-recent games with / without a point; at most one of them is non-zero.

### GET /api/players/standings

All players for the season, ordered by `points` descending.

- **Query params**: `season` (optional)

### GET /api/players/point-streaks

Players with an active point streak (`currentPointStreak > 0`), ordered by streak length descending.

- **Query params**: `season` (optional)

### GET /api/players/hot

Players ordered by recent form: `pointsPerLastNGames` descending. Players without a value are left out.

- **Query params**: `season` (optional)

### GET /api/players/{playerId}

Player detail. Same fields as the player row above.

- **Path params**: `playerId` (int) - NHL player ID
- **Query params**: `season` (optional)
- **Errors**: `404` `{ "error": "Player not found", "playerId": 8478402 }` when the player has no row for the season.

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

`timeOnIce` is in seconds. `gameWon` is `null` until the team's result for that game has been loaded.

---

## Teams

The three list endpoints below return the same team row:

```json
{
  "teamCode": "WPG",
  "teamName": "Winnipeg Jets",
  "logoUrl": "https://assets.nhle.com/logos/nhl/svg/WPG_light.svg",
  "gamesPlayed": 60,
  "wins": 40,
  "losses": 15,
  "overtimeLosses": 5,
  "points": 85,
  "pointPercentage": 0.708,
  "goalsFor": 210,
  "goalsAgainst": 150,
  "goalDifferential": 60,
  "conferenceName": "Western",
  "divisionName": "Central",
  "currentWinStreak": 7,
  "currentLossStreak": 0,
  "last10GamesPointPercentage": 0.8,
  "last10GamesPPG": 1.6
}
```

- `currentLossStreak` counts overtime losses as losses.
- `last10GamesPointPercentage` = points / (games × 2) over the last 10 games; `last10GamesPPG` = points per game over the same games.

### GET /api/teams/standings

All 32 teams, ordered by `points` descending.

- **Query params**: `season` (optional)

### GET /api/teams/win-streaks

Teams with an active win streak (`currentWinStreak > 0`), ordered by streak length descending.

- **Query params**: `season` (optional)

### GET /api/teams/loss-streaks

Teams with an active loss streak (`currentLossStreak > 0`), ordered by streak length descending.

- **Query params**: `season` (optional)

### GET /api/teams/{teamCode}

Team detail: the team row above plus the season's roster.

- **Path params**: `teamCode` (string) - Three-letter team code (e.g., `EDM`, `TOR`)
- **Query params**: `season` (optional)
- **Errors**: `404` `{ "error": "Team not found", "teamCode": "XYZ" }` when the team has no row for the season.

**Example `roster` entry:**
```json
{
  "playerId": 8478402,
  "fullName": "Connor McDavid",
  "positionCode": "C",
  "teamCode": "EDM",
  "headshotUrl": "https://assets.nhle.com/mugs/nhl/20252026/EDM/8478402.png",
  "goals": 32,
  "assists": 68,
  "points": 100
}
```

### GET /api/teams/{teamCode}/game-log

Team game results for a season, ordered by `gameDate` descending.

- **Path params**: `teamCode` (string) - Three-letter team code
- **Query params**: `season` (optional)

**Season scoping**: same as the player game log above.

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

### GET /api/teams/next-games

Each team's next unfinished regular-season or playoff game, ordered by `teamCode`. A game in
progress counts (`gameState` `LIVE`/`CRIT`), so a team playing right now shows its current opponent.
Teams with no game left are omitted.

- **Query params**: `season` (optional)

**Example response:**
```json
[
  {
    "teamCode": "TOR",
    "opponentTeamCode": "BOS",
    "homeGame": false,
    "gameDate": "2026-10-11",
    "startTimeUtc": "2026-10-11T23:00:00Z",
    "gameState": "FUT"
  }
]
```

---

## Games

### GET /api/games/latest

Scores for the homepage ticker. Returns today's games while any game is in progress (`live: true`),
otherwise the most recent day with finished games. Games are ordered by start time and goals in the
order scored; shootout goals are not included. Every player credited with a point carries two flags:

- `streakExtended`: the player also had a point in their previous game of the season.
- `hot`: the player's points per game over the last ten games is at least
  `scoreboard.hot-points-per-game` (default `1.5`).

Takes no parameters. Before any game has been played the response is
`{ "gameDate": null, "live": false, "games": [] }`.

**Example response:**
```json
{
  "gameDate": "2026-10-09",
  "live": false,
  "games": [
    {
      "gameId": 2026020066,
      "gameState": "OFF",
      "startTimeUtc": "2026-10-09T23:00:00Z",
      "awayTeamCode": "SEA",
      "awayScore": 6,
      "homeTeamCode": "DET",
      "homeScore": 3,
      "lastPeriodType": "REG",
      "goals": [
        {
          "goalNumber": 1,
          "period": 1,
          "periodType": "REG",
          "timeInPeriod": "09:19",
          "teamCode": "DET",
          "awayScore": 0,
          "homeScore": 1,
          "strength": "pp",
          "scorer": { "playerId": 8478042, "name": "V. Arvidsson", "streakExtended": true, "hot": false },
          "assists": [
            { "playerId": 8482078, "name": "L. Raymond", "streakExtended": false, "hot": true },
            { "playerId": 8481542, "name": "M. Seider", "streakExtended": false, "hot": false }
          ]
        }
      ]
    }
  ]
}
```

---

## Error Responses

| Status | Meaning | Body |
|--------|---------|------|
| 400 | A parameter has the wrong type (e.g. a non-numeric `playerId`) | `{ "error": "Invalid parameter", "detail": "..." }` |
| 404 | Unknown player or team for the season | `{ "error": "Player not found", "playerId": ... }` / `{ "error": "Team not found", "teamCode": "..." }` |
| 404 | Unknown path | `{ "error": "Not found", "path": "..." }` |
| 500 | Internal server error | `{ "error": "Internal server error" }` |
