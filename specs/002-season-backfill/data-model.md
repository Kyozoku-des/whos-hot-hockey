# Phase 1 Data Model: Historical Season Backfill

**Feature**: `002-season-backfill` | **Date**: 2026-09-13

No new tables. The feature writes four existing tables, adds finder methods to two repositories, and introduces two in-memory (non-persisted) types.

## Persisted entities

### `players` — `com.whoshot.nhl.domain.entity.Player`

**Unchanged.** Already season-scoped by `@EmbeddedId PlayerId(playerId, season)`.

| Concern | Rule for backfill |
|---|---|
| Key | `(player_id, season)` — season is the *requested* season, never the active one |
| Source | `/v1/player/{id}/landing` + `/v1/skater-stats-leaders/{season}/2` + `/v1/player/{id}/game-log/{season}/2` |
| Built by | Existing `PlayerFactory.createFromApiData(info, standing, gameLogs, seasonId)` — reused as-is |
| `isActive` filter | **Not applied** (R-002): retired players must load for past seasons |
| Validation | `points` summed from game logs must equal the standings total, else the player is skipped and recorded (R-012) |
| Idempotency | `save()` on the composite key updates in place |

`hot` / `cold` are left as the live sync leaves them — the backfill does not compute them, because "hot" is a statement about current form and is meaningless for a completed season.

### `teams` — `com.whoshot.nhl.domain.entity.Team`

**Unchanged.**

| Concern | Rule for backfill |
|---|---|
| Key | `(team_code, season)` via `TeamRepository.findByTeamCodeAndSeason` |
| Source | `/v1/standings/{regularSeasonEndDate}` (R-001) — final standings, not `/now` |
| Built by | Existing `DataSyncService.applyStandings(team, standing, seasonId)` — extracted for reuse |
| Idempotency | find-or-new on the natural key, as today |

### `game_logs` — `com.whoshot.nhl.domain.entity.GameLog`

**Unchanged schema; first writer in the codebase** (R-003). Unique on `(playerId, gameId)`.

| Field | Source |
|---|---|
| `playerId` | loop variable |
| `gameId` | `PlayerGameLogDto.gameId` |
| `gameDate` | `PlayerGameLogDto.gameDate` |
| `opponentTeamCode` | `PlayerGameLogDto.opponentAbbrev` |
| `homeGame` | `"H".equals(homeRoadFlag)` |
| `goals` / `assists` / `points` / `plusMinus` / `shots` | direct |
| `timeOnIce` | `toi` (`"mm:ss"`) parsed to seconds |
| `gameWon` | resolved from the `team_games` loaded earlier in the same run, keyed on `gameId` + the player's team for that game; `null` if unresolvable (R-005) |
| `seasonId` | requested season |
| `gameNumber` | dense 1..N in chronological order for that player in that season (R-006) |

### `team_games` — `com.whoshot.nhl.domain.entity.TeamGame`

**Unchanged schema; first writer in the codebase** (R-003). Unique on `(teamCode, gameId)`.

| Field | Source |
|---|---|
| `gameId` | `GameDto.id` |
| `teamCode` | the team whose schedule is being read |
| `gameDate` | `GameDto.startTimeUTC`, date part |
| `opponentTeamCode` | the other side of `homeTeam` / `awayTeam` |
| `homeGame` | `teamCode.equals(homeTeam.abbrev)` |
| `goalsFor` / `goalsAgainst` | `homeTeam.score` / `awayTeam.score`, oriented by `homeGame` — **requires adding `score` to `GameDto.TeamInfo`** (R-005) |
| `won` | `goalsFor > goalsAgainst` |
| `overtimeLoss` | lost **and** `gameOutcome.lastPeriodType` is `OT` or `SO` — **requires adding `gameOutcome` to `GameDto`** |
| `gameType` | `GameDto.gameType`, filtered to regular season (`2`) per spec A-002 |
| `seasonId` | requested season |
| `gameNumber` | dense 1..N chronological per team |

Only completed games are written; a game with a null score (scheduled, not yet played) is skipped.

### `current_season` — `com.whoshot.nhl.domain.entity.CurrentSeason`

**Never written by the backfill** (FR-005, R-008). Listed here to make the exclusion explicit and testable: the `is_active` row and `last_updated` must be byte-identical before and after a backfill run (SC-006).

## Repository additions

```java
// GameLogRepository
Optional<GameLog> findByPlayerIdAndGameId(Long playerId, Long gameId);

// TeamGameRepository
Optional<TeamGame> findByTeamCodeAndGameId(String teamCode, Long gameId);
List<TeamGame> findBySeasonId(String seasonId);   // batch lookup for gameWon resolution
```

## Transient types

### `BackfillRequest`

Validated, resolved description of what to load. Constructed once, before any write.

| Field | Notes |
|---|---|
| `seasonId` | `YYYYYYYY`, passes `SeasonValidator.isNotValidSeasonId` |
| `startDate` / `regularSeasonEndDate` | from the seasons endpoint; proves the season exists |
| `standingsDate` | `regularSeasonEndDate` formatted `YYYY-MM-DD` |
| `gameType` | fixed at `2` (A-002) |

**Validation order (FR-002)** — all before any database write:
1. season id present and non-blank → else `"no season specified; pass --backfill.season=YYYYYYYY"`
2. `SeasonValidator.isNotValidSeasonId(seasonId)` → else `"malformed season id"`
3. present in the seasons endpoint → else `"season does not exist upstream"`
4. `startDate` is in the past → else `"season has not started"`

### `BackfillSummary`

The FR-008 / FR-009 outcome record, logged at completion.

| Field | Notes |
|---|---|
| `seasonId`, `startedAt`, `finishedAt`, `duration` | |
| `teamsWritten`, `teamGamesWritten`, `playersWritten`, `gameLogsWritten` | counts |
| `skipped` | list of `(kind, identifier, reason)` — the FR-007 skip list |
| `success` | false if a fatal precondition failed; drives the exit code |

## Load order

Dictated by the `gameWon` dependency (R-005):

```
validate request
  └─ acquire advisory lock (R-011)
       ├─ 1. teams          (final standings for the season)
       ├─ 2. team games     (per-team season schedule)
       ├─ 3. players        (skater leaders → landing → game log)
       └─ 4. player game logs  (gameWon resolved against step 2)
     release lock → emit summary → exit code
```
