# Phase 0 Research: Historical Season Backfill

**Feature**: `002-season-backfill` | **Date**: 2026-09-13

## R-001: Does the upstream API serve *historical* team standings?

**Question**: Spec edge case "Historical team standings" — `DataSyncService.persistStandings()` calls `NhlApiService.getTeamStandings()`, which hits `/v1/standings/now`. Backfilling with that would write *today's* standings under an old season id.

**Decision**: Use `GET /v1/standings/{YYYY-MM-DD}`, with the date taken from the target season's `regularSeasonEndDate` (already available on `SeasonDto` from `https://api.nhle.com/stats/rest/en/season`).

**Evidence**: Probed live — `GET https://api-web.nhle.com/v1/standings/2025-04-17` returns `200` with the same `standings[]` shape as `/now`, plus a `date` field echoing the requested date and `gameTypeId: 2`. Response deserializes into the existing `StandingsResponseDto` / `TeamStandingsDto` unchanged.

**Rationale**: Point-in-time standings at the regular-season end date *are* the final standings for that season, including `l10*` and `streak*` fields. No new DTOs needed.

**Alternatives considered**:
- `/v1/standings/now` + season filter — rejected: the endpoint has no season parameter; it always describes today.
- `https://api.nhle.com/stats/rest/en/team/summary?cayenneExp=seasonId=20242025` — rejected: different response shape, would need new DTOs, and lacks the streak/L10 fields `applyStandings()` depends on.

**Implication**: `NhlApiService.getTeamStandings()` gains a date-parameterised overload. `/now` remains the path for the live sync.

---

## R-002: Do the player endpoints accept a past season?

**Decision**: Yes — no change needed.

**Evidence**: Probed live, both `200`:
- `GET /v1/skater-stats-leaders/20242025/2?categories=points&limit=-1`
- `GET /v1/club-schedule-season/MTL/20242025`

`/v1/player/{id}/game-log/{season}/{gameType}` and `/v1/player/{id}/landing` are already season-parameterised or season-independent.

**Caveat**: `/v1/player/{id}/landing` reports `isActive` as of *today*. `DataSyncService.syncPlayers()` skips `!playerInfo.isActive()` players — applying that filter to a backfill would silently drop every player who has since retired. The backfill must not filter on `isActive` (spec edge case "Players who … have since retired").

**Caveat 2**: `skater-stats-leaders` returns skaters only; goalies are absent. This matches current-season behaviour, so backfilled seasons stay consistent with live ones. No change.

---

## R-003: What actually populates `game_logs` and `team_games` today?

**Decision**: **Nothing does.** Both tables are write-free in the current codebase.

**Evidence**: `GameLogRepository` is injected into `DataSyncService` (line 43) and never used. `TeamGameRepository` is not referenced by `data-job` at all. The only readers are `PlayerService.getPlayerGameLog()` and `TeamService.getTeamGameLog()`, which serve `/players/{id}/game-log` and `/teams/{code}/game-log`. Those endpoints therefore return `[]` for every season, including the current one.

**Consequence for this feature**: FR-003 requires the backfill to write per-game logs, so the write path must be built here regardless. The same writer, applied to the current season, is what makes the graph show anything at all.

**Decision**: Build the game-log write path as a season-parameterised component shared by the backfill *and* the live sync, and wire the live sync to it. Justification: SC-007 ("a visitor sees a previous-season comparison baseline") is unobservable if the current-season line is empty — the feature would be verifiable only by SQL query, not by its stated success criterion. The extra cost is one call site in the live sync path.

**Alternatives considered**:
- Backfill writes game logs, live sync left alone — rejected: satisfies FR-003 but not SC-007; ships a graph with one line and an empty axis.
- Fix the live sync in a separate issue first — rejected: creates an ordering dependency for no benefit; the writer is the same code either way.

---

## R-004: Where does the frontend stand?

**Decision**: No frontend work in this feature.

**Evidence**: `PlayerGameLogGraph.vue` and `TeamGameLogGraph.vue` already accept `currentSeasonData` and `previousSeasonData` props and render a two-line comparison. `PlayerPage.vue:119-122` and `TeamPage.vue:110-113` already compute the previous season and issue a second `getPlayerGameLog(id, previousSeason)` / `getTeamGameLog(code, previousSeason)` call. `useApi.js:63-66, 104-107` already pass `?season=`. Both graphs already render "No game log data available" when both arrays are empty, which satisfies FR-012's "distinguish absent baseline from zero".

US2 is therefore delivered purely by putting data in the database.

---

## R-005: How is `gameWon` on a player game log obtained?

**Question**: `GameLog.gameWon` is non-derivable from `PlayerGameLogDto` — the upstream player game-log payload carries no result field.

**Decision**: Load team games first, then resolve `gameWon` by looking up `(gameId, teamCode)` in the team-game data loaded in the same run. Where no match exists, leave `null`.

**Rationale**: The team schedule (`/v1/club-schedule-season/{team}/{season}`) already carries the result, so this costs no extra upstream requests. Fixes the load order: teams → team games → players → player game logs.

**Alternatives considered**:
- One `/v1/gamecenter/{id}/boxscore` call per game — rejected: ~1300 extra requests per season for a field already available.
- Leave `gameWon` always null — rejected: it is a real column the graph tooltip can use, and the data is free.

**Required change**: `GameDto` currently exposes only `id`, `season`, `startTimeUTC`, `gameType`, `gameState`, `homeTeam.abbrev`, `awayTeam.abbrev`. It needs `homeTeam.score`, `awayTeam.score`, and `gameOutcome.lastPeriodType` (to distinguish a regulation loss from an OT/SO loss for `TeamGame.overtimeLoss`). `@JsonIgnoreProperties(ignoreUnknown = true)` is already set, so adding fields is safe for existing callers.

---

## R-006: How is `gameNumber` assigned?

**Decision**: Sort the season's games chronologically by date and assign 1..N per player / per team within that season.

**Rationale**: `PlayerService.getPlayerGameLog()` orders by `game_number ASC` and the graph plots against it, so it must be a dense chronological index, not the NHL game id. Upstream returns player game logs most-recent-first, so the list is reversed before numbering.

**Edge case**: a player traded mid-season has a single continuous 1..N sequence across both teams, because the upstream game log is per-player, not per-team.

---

## R-007: Idempotency strategy (FR-006)

**Decision**: Upsert on natural keys — `players (player_id, season)` via the existing `@EmbeddedId`, `teams (team_code, season)` via `findByTeamCodeAndSeason`, `game_logs (playerId, gameId)` and `team_games (teamCode, gameId)` via their existing `@UniqueConstraint`s, with new finder methods on the two repositories.

**Rationale**: Every target table already has a natural key with a unique constraint, so "find-or-new, then save" converges without duplicates, and a re-run after a partial failure simply overwrites what it already wrote. This is the pattern `persistStandings()` already uses for teams.

**Alternatives considered**:
- `DELETE FROM … WHERE season_id = ?` then bulk insert — rejected: destroys good data if the re-run then fails partway, violating the spirit of FR-004.
- JPA `merge` on a detached entity with a surrogate id — rejected: `GameLog`/`TeamGame` use `IDENTITY` surrogate keys, so merge without a prior lookup inserts duplicates.
- Postgres `ON CONFLICT DO UPDATE` via native query — rejected for now: faster, but the per-batch lookup is not the bottleneck (upstream HTTP is), and it would bypass the entity mapping.

---

## R-008: Preventing the backfill from touching the active season (FR-005)

**Problem**: `DataSyncService.init()` is `@PostConstruct` — merely starting the `data-job` process resolves the current season, calls `persistCurrentSeason()` (which flips `is_active` rows), and fetches today's schedule. A backfill process that boots the same context would mutate the active-season marker before doing any work.

**Decision**: Move current-season resolution out of `@PostConstruct` into an explicitly invoked method, called by the live-sync startup path (`SchedulingConfig` / `DynamicSchedulingService`) and *not* by the backfill entry point. The backfill resolves its own season from the `seasons` endpoint and never calls `persistCurrentSeason()`.

**Rationale**: Construction-time side effects are what make FR-005 hard to guarantee; removing them makes the guarantee structural rather than a matter of which beans happen to load. It also removes the cause of the known problem that `NhlApiServiceTest` mutates the real dev database on context boot.

**Alternatives considered**:
- `@Profile`-guard the `@PostConstruct` — rejected: the bean is still constructed, so the guard has to be an `if` inside the side effect; fragile and leaves the test-pollution problem in place.
- Separate Spring context for the backfill — rejected as over-engineering (Principle III); shares no code and duplicates configuration.

---

## R-009: Invocation mechanism

**Decision**: A `CommandLineRunner` activated by the `backfill` Spring profile, reading the target season from the `backfill.season` property, mirroring the existing `initial-load` profile / `InitialLoadRunner` pattern. It exits with a non-zero status on failure.

```bash
java -jar data-job.jar --spring.profiles.active=backfill --backfill.season=20242025
```

**Rationale**: Reuses the pattern already in the repo (Principle III), needs no new dependency (no Picocli / Spring Shell), runs against a deployed environment without redeploying the API (FR-010), and gives FR-009's exit-code signal for free. `spring.main.web-application-type=none` is already set.

**Alternatives considered**:
- Admin REST endpoint on the `api` module — rejected: spec A-003 explicitly excludes a public trigger, and it would put a multi-hour job in a request thread.
- Standalone SQL/script outside Spring — rejected: would duplicate the DTO, statistics, and entity-mapping logic.

---

## R-010: Rate limiting and runtime (FR-013, A-007)

**Decision**: Configurable fixed delay between upstream requests (`backfill.request-delay-ms`, default 100ms), applied in the backfill path only. Keep the existing `@Retryable(delay = 1000, multiplier = 2)` on `ApiClient` for transient failures.

**Estimate**: ~1000 skaters × 2 requests (landing + game log) + 32 team schedules + 2 = ~2100 requests. At 100ms pacing plus response time, roughly 10–25 minutes per season. Consistent with A-007's "tens of minutes".

**Rationale**: The NHL public API publishes no documented rate limit, so a conservative, tunable pace is the defensible default. Pacing only the backfill avoids slowing the live in-game sync.

---

## R-011: Concurrency guard (FR-014)

**Decision**: Postgres session-level advisory lock (`pg_try_advisory_lock`) keyed on a constant + season id, taken at the start of the run. If not acquired, log and exit non-zero with "backfill already running for season X".

**Rationale**: One query, no new table, no new dependency, and the lock is released automatically if the process dies — which a `backfill_runs` status row would not be.

**Alternatives considered**:
- A `backfill_runs` table with a status column — rejected: a crashed run leaves a permanent `RUNNING` row that blocks all future runs until manually cleared.
- No guard, document "don't do that" — rejected: FR-014 is explicit.

---

## R-012: Per-record failure isolation (FR-007)

**Problem**: `StatisticsCalculationService.calculatePlayerStatistics()` throws `PlayerStatisticsException` when the standings points total disagrees with the summed game logs, and `syncPlayers()` lets it propagate out of the whole run. In a backfill, one bad player would abort the season.

**Decision**: The backfill catches `PlayerStatisticsException` and `ApiClientException` per player, records `(playerId, reason)` in the run summary, and continues. A failure fetching the season list or the standings is still fatal — those are whole-run preconditions, not per-record faults.

**Rationale**: Matches FR-007's split between "skip the record" and "fail the run", and keeps the existing validation as a real check rather than deleting it.

---

## Residual risks

| Risk | Mitigation |
|------|-----------|
| Relocated/renamed franchises (e.g. ARI → UTA) produce two `teams` rows keyed on different codes across seasons | Accepted and correct — spec requires loading a team "under the identity it had that season". The team game-log lookup is `(teamCode, seasonId)`, so it resolves per season. |
| `/v1/standings/{date}` for a season that predates the endpoint's coverage | Caught by FR-002 validation plus a fail-loud check that the standings response is non-empty. |
| Upstream schema drift between seasons | All DTOs are `@JsonIgnoreProperties(ignoreUnknown = true)`; missing fields deserialize to `null` rather than failing. |
| `SeasonValidator.getCurrentSeason()` is dead code returning `null` | Unrelated pre-existing defect; noted, removed opportunistically if touched, not a goal of this feature. |
