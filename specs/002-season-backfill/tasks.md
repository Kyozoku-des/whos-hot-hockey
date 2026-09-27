---

description: "Task list for 002-season-backfill"
---

# Tasks: Historical Season Backfill

**Input**: Design documents from `/specs/002-season-backfill/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/cli-contract.md, quickstart.md

**Tests**: Included and mandatory. Constitution Principle I (Test-First Development) is NON-NEGOTIABLE — every test task must be written and observed failing before its implementation task.

**Organization**: Grouped by user story so each can be implemented, tested, and demoed independently.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: US1 / US2 / US3 from spec.md
- All paths are repository-relative

## Path Conventions

- Data job main: `backend/data-job/src/main/java/com/whoshot/nhl/datajob/`
- Data job test: `backend/data-job/src/test/java/com/whoshot/nhl/datajob/`
- Domain: `backend/domain/src/main/java/com/whoshot/nhl/domain/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Configuration and test scaffolding the rest of the work needs

- [X] T001 Add `backfill` profile properties (`backfill.season`, `backfill.request-delay-ms=100`, `backfill.dry-run=false`) to `backend/data-job/src/main/resources/application.properties`
- [X] T002 [P] Add Testcontainers PostgreSQL dependency (test scope) to `backend/data-job/pom.xml`, matching the version already used elsewhere in the build
- [X] T003 [P] Create reusable Testcontainers base class `backend/data-job/src/test/java/com/whoshot/nhl/datajob/backfill/PostgresIntegrationTestBase.java` with a `@ServiceConnection` PostgreSQL 16 container, so integration tests never touch the dev database
- [X] T004 [P] Add a `backfill` profile service definition (no-restart, run-once) to `docker-compose.yml` so the job can be invoked per `contracts/cli-contract.md`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Season-parameterised plumbing. Corresponds to plan.md Phase A. Behaviour-preserving — the live sync must work exactly as before.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

### Tests (write first, must fail)

- [X] T005 [P] Write test in `backend/data-job/src/test/java/com/whoshot/nhl/datajob/service/DataSyncServiceLifecycleTest.java` asserting that constructing `DataSyncService` performs no API call and no write to `current_season` (proves the `@PostConstruct` side effect is gone — FR-005, research R-008)
- [X] T006 [P] Write test in `backend/data-job/src/test/java/com/whoshot/nhl/datajob/service/NhlApiServiceStandingsDateTest.java` asserting `getTeamStandings("2025-04-17")` requests `/v1/standings/2025-04-17` and that `getTeamStandings()` still requests `/v1/standings/now` (research R-001)
- [X] T007 [P] Write test in `backend/data-job/src/test/java/com/whoshot/nhl/datajob/dto/GameDtoDeserializationTest.java` asserting a `club-schedule-season` payload deserializes `homeTeam.score`, `awayTeam.score`, and `gameOutcome.lastPeriodType` (research R-005)

### Implementation

- [X] T008 Remove the `@PostConstruct` annotation from `DataSyncService.init()` in `backend/data-job/src/main/java/com/whoshot/nhl/datajob/service/DataSyncService.java` and expose current-season resolution as an explicit public method
- [X] T009 Invoke that method from the live-sync startup path in `backend/data-job/src/main/java/com/whoshot/nhl/datajob/config/SchedulingConfig.java` (and/or `service/DynamicSchedulingService.java`) so live behaviour is unchanged
- [X] T010 Extract `applyStandings` and `persistStandings` in `backend/data-job/src/main/java/com/whoshot/nhl/datajob/service/DataSyncService.java` to take an explicit `seasonId` and a standings list argument rather than reading the `season` field
- [X] T011 [P] Add `getTeamStandings(String date)` overload hitting `/v1/standings/{date}` to `backend/data-job/src/main/java/com/whoshot/nhl/datajob/service/NhlApiService.java`, keeping the existing no-arg `/now` method
- [X] T012 [P] Add `score` to `GameDto.TeamInfo` and a `gameOutcome.lastPeriodType` nested type to `backend/data-job/src/main/java/com/whoshot/nhl/datajob/dto/nhlapi/GameDto.java`
- [X] T013 [P] Add `Optional<GameLog> findByPlayerIdAndGameId(Long, Long)` to `backend/domain/src/main/java/com/whoshot/nhl/domain/repository/GameLogRepository.java`
- [X] T014 [P] Add `Optional<TeamGame> findByTeamCodeAndGameId(String, Long)` and `List<TeamGame> findBySeasonId(String)` to `backend/domain/src/main/java/com/whoshot/nhl/domain/repository/TeamGameRepository.java`
- [X] T015 Verify `cd backend && mvn test` passes with the existing `DataSyncServiceSeasonResolutionTest`, `InitialDataLoadServiceTest`, and API module tests green — the refactor must not change live-sync behaviour

**Checkpoint**: Season-parameterised foundations in place; the live sync is unchanged and no longer mutates state at construction time

---

## Phase 3: User Story 1 - Load a named past season on demand (Priority: P1) 🎯 MVP

**Goal**: A maintainer names a season, runs one command, and that season's teams, team games, players, and per-game player logs land in the database, attributed to that season and nothing else.

**Independent Test**: Against a database holding only the current season, run `--spring.profiles.active=backfill --backfill.season=20242025`, then confirm via `GET /players?season=20242025`, `GET /players/{id}/game-log?season=20242025`, and `GET /teams/{code}/game-log?season=20242025` that data is present, and that current-season rows and the `current_season` table are unchanged.

### Tests for User Story 1 (write first, must fail) ⚠️

- [X] T016 [P] [US1] Write unit test in `backend/data-job/src/test/java/com/whoshot/nhl/datajob/model/BackfillRequestValidationTest.java` covering the four validation steps in order — missing, malformed, unknown-upstream, not-yet-started — each with its distinct message (FR-002, spec AS-3)
- [X] T017 [P] [US1] Write unit test in `backend/data-job/src/test/java/com/whoshot/nhl/datajob/service/TeamGameMapperTest.java` for home/away orientation of `goalsFor`/`goalsAgainst`, `won`, `overtimeLoss` from `lastPeriodType` OT/SO, chronological `gameNumber`, and skipping games with a null score (data-model.md `team_games`)
- [X] T018 [P] [US1] Write unit test in `backend/data-job/src/test/java/com/whoshot/nhl/datajob/service/GameLogMapperTest.java` for `toi` `"mm:ss"` → seconds, `homeGame` from `homeRoadFlag`, reversal of the most-recent-first upstream list into dense 1..N `gameNumber`, and `gameWon` resolved from loaded team games with `null` when unresolvable (research R-005, R-006)
- [X] T019 [P] [US1] Write unit test in `backend/data-job/src/test/java/com/whoshot/nhl/datajob/service/SeasonBackfillServiceTest.java` (mocked `NhlApiService`) asserting the load order teams → team games → players → player game logs, and that retired players (`isActive=false`) are **not** skipped (research R-002)
- [X] T020 [P] [US1] Write integration test in `backend/data-job/src/test/java/com/whoshot/nhl/datajob/backfill/BackfillSeasonIsolationIT.java` extending the Testcontainers base: seed current-season rows plus an active `current_season`, run a backfill of a different season, assert current-season row counts and the `current_season` row are byte-identical afterwards (FR-004, FR-005, SC-006)
- [X] T021 [P] [US1] Write integration test in `backend/data-job/src/test/java/com/whoshot/nhl/datajob/backfill/BackfillTotalsConsistencyIT.java` asserting every loaded player's `points` equals the sum of that player's loaded `game_logs.points` (SC-003)
- [X] T022 [P] [US1] Write integration test in `backend/data-job/src/test/java/com/whoshot/nhl/datajob/backfill/BackfillExitCodeIT.java` asserting exit code 1 with zero writes for an invalid season, and exit code 0 for a successful load (FR-009, contracts/cli-contract.md)

### Implementation for User Story 1

- [X] T023 [P] [US1] Create `BackfillRequest` record in `backend/data-job/src/main/java/com/whoshot/nhl/datajob/model/BackfillRequest.java` with `seasonId`, `startDate`, `regularSeasonEndDate`, `standingsDate`, `gameType`
- [X] T024 [US1] Implement the four-step validation as a factory on `BackfillRequest` (or a `BackfillRequestFactory` in the same package), reusing `SeasonValidator.isNotValidSeasonId` and `NhlApiService.getSeasons()`, guaranteeing no database write occurs before it passes (T016 green)
- [X] T025 [US1] Create `BackfillRunner` in `backend/data-job/src/main/java/com/whoshot/nhl/datajob/config/BackfillRunner.java` as a `@Profile("backfill")` `CommandLineRunner` mirroring `InitialLoadRunner`, reading `backfill.season` and `backfill.dry-run`, and setting the process exit code per contracts/cli-contract.md (T022 green)
- [X] T026 [US1] Create `SeasonBackfillService` skeleton in `backend/data-job/src/main/java/com/whoshot/nhl/datajob/service/SeasonBackfillService.java` with the four ordered phases and a dry-run short-circuit
- [X] T027 [US1] Implement team loading in `SeasonBackfillService`: fetch `getTeamStandings(request.standingsDate())`, persist via the season-parameterised standings method from T010, fail the run if the standings response is empty (research R-001, residual risks)
- [X] T028 [US1] Create `GameLogWriter` in `backend/data-job/src/main/java/com/whoshot/nhl/datajob/service/GameLogWriter.java` with team-game mapping and upsert on `(teamCode, gameId)` using T014's finder (T017 green)
- [X] T029 [US1] Implement team-game loading in `SeasonBackfillService`: per team, `getTeamSchedule(teamCode, seasonId)`, filter to `gameType == 2` and completed games, delegate to `GameLogWriter`
- [X] T030 [US1] Add player-game-log mapping and upsert on `(playerId, gameId)` to `GameLogWriter`, including `gameNumber` assignment and `gameWon` resolution against the team games loaded in the same run (T018 green)
- [X] T031 [US1] Implement player loading in `SeasonBackfillService`: `getPlayerStandingsOrder(seasonId, 2)` → per player `getPlayerInfo` + `getPlayerGameLogs` → existing `PlayerFactory.createFromApiData` → save; **no `isActive` filter** (T019 green)
- [X] T032 [US1] Wire player game logs into the player loop via `GameLogWriter`, streaming per player so no whole-season collection is held in memory (plan.md Constraints)
- [X] T033 [US1] Add Javadoc to all new classes matching the existing `data-job` density (Constitution Principle II)

**Checkpoint**: US1 complete — a named past season loads end to end, isolated from the current season. This is the MVP.

---

## Phase 4: User Story 2 - Compare current form against last season in graphs (Priority: P2)

**Goal**: A visitor opening a player or team page sees the previous season as a comparison line on the game-log graph.

**Independent Test**: With 20242025 backfilled and the current season syncing, open a player page and a team page; both graphs draw two lines. Open a rookie's page; the graph shows only the current line and no error.

**Note**: The frontend is already wired (`PlayerPage.vue:119-122`, `TeamPage.vue:110-113`, both graph components accept `previousSeasonData`). This phase is data-side only. It includes wiring the live sync to `GameLogWriter`, because nothing currently writes current-season game logs — without it the graph has no current line and SC-007 is unverifiable through the product (research R-003, plan.md Complexity Tracking).

### Tests for User Story 2 (write first, must fail) ⚠️

- [X] T034 [P] [US2] Write integration test in `backend/data-job/src/test/java/com/whoshot/nhl/datajob/backfill/LiveSyncGameLogWriteIT.java` asserting that a current-season sync writes `game_logs` and `team_games` rows for the synced players and teams (research R-003)
- [X] T035 [P] [US2] Write integration test in `backend/api/src/test/java/com/whoshot/nhl/api/service/SeasonScopedGameLogIT.java` asserting `getPlayerGameLog(id, "20242025")` returns rows in ascending `game_number` and `getTeamGameLog(code, "20242025")` returns rows in descending `game_date`, with backfilled data present (FR-011)
- [X] T036 [P] [US2] Write test asserting a player absent from the backfilled season returns an empty list rather than an error or zeroed rows, in `backend/api/src/test/java/com/whoshot/nhl/api/service/SeasonScopedGameLogIT.java` (FR-012, spec US2 AS-2)

### Implementation for User Story 2

- [X] T037 [US2] Call `GameLogWriter` from the live player sync in `backend/data-job/src/main/java/com/whoshot/nhl/datajob/service/DataSyncService.java` (`syncPlayers` and `syncPlayersForTeams`) so current-season game logs are persisted (T034 green)
- [X] T038 [US2] Call `GameLogWriter` for team games from the live team sync path in `DataSyncService`, using the season schedule for the active season
- [X] T039 [US2] Remove the now-genuinely-used `GameLogRepository` injection comment/dead state in `DataSyncService` and confirm no unused repository injections remain
- [ ] T040 [US2] Manually verify per quickstart.md: backfill 20242025, open a player page and a team page, confirm two lines render and a rookie's page degrades cleanly (SC-007)

**Checkpoint**: US1 and US2 both work — past seasons load and the comparison graphs render

---

## Phase 5: User Story 3 - Re-run a load safely after failure or for correction (Priority: P3)

**Goal**: The load survives interruption and hostile upstream behaviour: re-runnable without duplicates, skips individually broken records, reports what it did, paces its requests, and refuses to run twice at once.

**Independent Test**: Run the same season twice and confirm identical counts and no duplicates. Force an upstream failure for one player and confirm the run completes and names that player in the summary. Start a second run while one is in flight and confirm it exits 3.

### Tests for User Story 3 (write first, must fail) ⚠️

- [X] T041 [P] [US3] Write integration test in `backend/data-job/src/test/java/com/whoshot/nhl/datajob/backfill/BackfillIdempotencyIT.java` asserting a second run of the same season produces identical row counts across `players`, `teams`, `game_logs`, `team_games` with no duplicates (FR-006, SC-004)
- [X] T042 [P] [US3] Write integration test in `backend/data-job/src/test/java/com/whoshot/nhl/datajob/backfill/BackfillResumeIT.java` asserting that a run interrupted partway, then re-run, yields the same final state as an uninterrupted run (spec US3 AS-2)
- [X] T043 [P] [US3] Write unit test in `backend/data-job/src/test/java/com/whoshot/nhl/datajob/service/BackfillFailureIsolationTest.java` asserting a `PlayerStatisticsException` and an exhausted-retry `ApiClientException` each skip only that player and appear in the summary with a reason, while a failed seasons/standings fetch is fatal (FR-007, research R-012)
- [X] T044 [P] [US3] Write unit test in `backend/data-job/src/test/java/com/whoshot/nhl/datajob/model/BackfillSummaryTest.java` asserting the rendered summary contains season, duration, all four counts, the skip list with reasons, and the result line, in the format of contracts/cli-contract.md (FR-008)
- [X] T045 [P] [US3] Write integration test in `backend/data-job/src/test/java/com/whoshot/nhl/datajob/backfill/BackfillConcurrencyIT.java` asserting a second concurrent run for the same season exits 3 and writes nothing, and that the lock releases when the first run's connection closes (FR-014, research R-011)

### Implementation for User Story 3

- [X] T046 [P] [US3] Create `BackfillSummary` in `backend/data-job/src/main/java/com/whoshot/nhl/datajob/model/BackfillSummary.java` with counts, the `(kind, identifier, reason)` skip list, timings, and success flag plus its rendered form (T044 green)
- [X] T047 [US3] Add per-record try/catch around the player loop and the team loop in `SeasonBackfillService`, recording skips into `BackfillSummary` and keeping seasons/standings failures fatal with exit code 2 (T043 green)
- [X] T048 [US3] Add progress logging every 25 records in `SeasonBackfillService` in the format `Backfill {season}: phase={phase} {n}/{total} ({pct}%) elapsed={hh:mm:ss}` (FR-008)
- [X] T049 [P] [US3] Create `BackfillLockService` in `backend/data-job/src/main/java/com/whoshot/nhl/datajob/service/BackfillLockService.java` using `pg_try_advisory_lock` keyed on a constant plus the season id, and acquire it at the start of `SeasonBackfillService` (T045 green)
- [X] T050 [P] [US3] Add configurable request pacing (`backfill.request-delay-ms`, default 100) applied in the backfill path only, leaving the live sync unthrottled (FR-013, research R-010)
- [X] T051 [US3] Confirm upsert-on-natural-key is used for all four tables and that no code path deletes by season (T041, T042 green — FR-006, research R-007)

**Checkpoint**: All three user stories independently functional

---

## Phase 6: Polish & Cross-Cutting Concerns

- [X] T052 [P] Add a "Backfilling a past season" section to `backend/README.md`, sourced from `specs/002-season-backfill/quickstart.md` (Constitution Principle II)
- [X] T053 [P] Add a note to `API_CONTRACT.md` (root file is empty since 001; note added to `backend/API_REFERENCE.md`) that `/players/{id}/game-log` and `/teams/{code}/game-log` return data for any loaded season via `?season=`, and that an unloaded season returns an empty list — no endpoint change
- [X] T054 [P] Remove the dead `SeasonValidator.getCurrentSeason()` method (returns `null`, unused) from `backend/data-job/src/main/java/com/whoshot/nhl/datajob/util/SeasonValidator.java` (research residual risks)
- [ ] T055 Run a real full-season backfill of 20242025 against a dev database and record the actual duration, request count, and skip-list size; if the points-mismatch skip rate is high, raise it as a finding rather than silently accepting it (plan.md Risks)
- [ ] T056 Execute every verification step in `specs/002-season-backfill/quickstart.md` end to end, including the SQL checks and the browser check
- [X] T057 Run `cd backend && mvn test` and confirm the full suite is green, including the pre-existing API and data-job tests

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies
- **Foundational (Phase 2)**: Depends on Setup — BLOCKS all user stories
- **US1 (Phase 3)**: Depends on Foundational. No dependency on US2 or US3
- **US2 (Phase 4)**: Depends on Foundational; needs `GameLogWriter` from US1 (T028, T030) before T037/T038
- **US3 (Phase 5)**: Depends on Foundational; hardens the US1 load path, so needs T026–T032
- **Polish (Phase 6)**: After the desired stories are complete

### Story Dependencies

Unlike the template's default, these stories are **not** fully parallel: US2 and US3 both build on the US1 load path. US1 alone is a shippable increment; US2 and US3 can be worked in parallel with each other once US1 lands.

### Within Each Story

- Tests written and observed failing before implementation (Constitution Principle I — a PR that inverts this is grounds for rejection)
- Models → services → runner wiring → integration
- T027 (teams) before T029 (team games) before T030–T032 (player game logs), because `gameWon` resolves against team games

### Parallel Opportunities

- Setup: T002, T003, T004 together
- Foundational tests: T005, T006, T007 together; then implementation T011, T012, T013, T014 together (T008–T010 touch `DataSyncService` and must be serial)
- US1 tests: T016–T022 all together
- US1 implementation: T023 parallel with nothing else initially; T027/T028 touch different files and can overlap once T026 exists
- US3 tests: T041–T045 all together; implementation T046, T049, T050 together
- Polish: T052, T053, T054 together

---

## Parallel Example: User Story 1

```bash
# All US1 tests first — they must all fail before any implementation starts:
Task: "Validation test in .../model/BackfillRequestValidationTest.java"
Task: "Team game mapping test in .../service/TeamGameMapperTest.java"
Task: "Player game log mapping test in .../service/GameLogMapperTest.java"
Task: "Orchestration + retired-player test in .../service/SeasonBackfillServiceTest.java"
Task: "Season isolation IT in .../backfill/BackfillSeasonIsolationIT.java"
Task: "Totals consistency IT in .../backfill/BackfillTotalsConsistencyIT.java"
Task: "Exit code IT in .../backfill/BackfillExitCodeIT.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1: Setup
2. Phase 2: Foundational — **critical, and the riskiest work in the feature**: it refactors the live sync's `@PostConstruct`. T015 is the gate.
3. Phase 3: US1
4. **STOP and VALIDATE**: backfill 20242025, verify with the quickstart SQL, confirm the current season is untouched
5. Ship — past-season data is queryable through the existing API

### Incremental Delivery

1. Setup + Foundational → live sync unchanged, no construction-time side effects
2. + US1 → a named season loads (MVP, closes issue #17's literal ask)
3. + US2 → graphs render the comparison (closes issue #17's motivation)
4. + US3 → operationally safe to re-run

### Notes

- [P] = different files, no incomplete dependencies
- Verify each test fails before implementing against it
- Commit per task or logical group; branch is `002-season-backfill`
- Integration tests use Testcontainers, never the dev database — the existing `NhlApiServiceTest` mutating dev Postgres is the failure mode to avoid, and T008 removes its root cause
