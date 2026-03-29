# Tasks: Backend Modularization - API & Data Job Separation

**Input**: Design documents from `/specs/001-api-data-split/`
**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/api-endpoints.md, quickstart.md

**Tests**: Included — spec explicitly requires tests for statistics calculations (FR-023), and constitution Principle I mandates test-first development for all backend logic and API endpoints.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: PostgreSQL migration, Docker setup, API module independence

- [x] T001 Create `docker-compose.yml` at repository root with PostgreSQL 16 service (db: `nhl_stats`, user: `whoshot`, password: `whoshot`, port: 5432)
- [x] T002 Update `backend/pom.xml` parent POM: replace `sqlite-jdbc` with `org.postgresql:postgresql` in `<dependencyManagement>`
- [x] T003 [P] Update `backend/domain/pom.xml`: replace SQLite JDBC + Hibernate Community Dialects dependencies with PostgreSQL driver
- [x] T004 [P] Update `backend/api/pom.xml`: remove dependency on `data-job` module, keep only `domain` dependency. Add PostgreSQL driver. Add Spring Boot Starter Web. Add `spring-boot-maven-plugin` for independent execution.
- [x] T005 [P] Update `backend/data-job/pom.xml`: replace SQLite JDBC + Hibernate Community Dialects dependencies with PostgreSQL driver
- [x] T006 Create `backend/api/src/main/java/com/whoshot/nhl/api/ApiApplication.java` with `@SpringBootApplication`, `@EntityScan("com.whoshot.nhl.domain.entity")`, `@EnableJpaRepositories("com.whoshot.nhl.domain.repository")`
- [x] T007 [P] Update `backend/api/src/main/resources/application.properties`: replace SQLite config with PostgreSQL connection (jdbc:postgresql://localhost:5432/nhl_stats, username, password, driver), remove `hikari.maximum-pool-size=1`
- [x] T008 [P] Update `backend/data-job/src/main/resources/application.properties`: replace SQLite config with PostgreSQL connection, remove `hikari.maximum-pool-size=1`, remove `app.sqlite.path`
- [x] T009 [P] Remove `backend/domain/src/main/resources/application.properties` (empty file, not needed)

**Checkpoint**: Both API and data-job modules can start independently against Docker PostgreSQL. Run `docker compose up -d`, then `cd backend && mvn clean install -DskipTests` to verify compilation.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

**CRITICAL**: No user story work can begin until this phase is complete

- [x] T010 Add unique constraint annotation on GameLog entity for `(playerId, gameId)` in `backend/domain/src/main/java/com/whoshot/nhl/domain/entity/GameLog.java` using `@Table(uniqueConstraints = ...)` for duplicate detection (FR-013)
- [x] T011 [P] Add unique constraint annotation on TeamGame entity for `(teamCode, gameId)` in `backend/domain/src/main/java/com/whoshot/nhl/domain/entity/TeamGame.java` using `@Table(uniqueConstraints = ...)` for duplicate detection (FR-013)
- [x] T012 Create `backend/api/src/main/java/com/whoshot/nhl/api/config/CorsConfig.java` implementing `WebMvcConfigurer` with CORS mapping for `http://localhost:3000` (FR-008)
- [x] T013 Create `backend/api/src/main/java/com/whoshot/nhl/api/config/GlobalExceptionHandler.java` with `@ControllerAdvice` handling 400, 404, 500 error responses per contracts/api-endpoints.md (FR-007)
- [x] T014 Add `TeamGameRepository` method `findByTeamCodeAndSeasonIdOrderByGameDateDesc(teamCode, seasonId)` in `backend/domain/src/main/java/com/whoshot/nhl/domain/repository/TeamGameRepository.java` (needed for team game log endpoint). Create the repository if it does not exist.

**Checkpoint**: Foundation ready — API module starts on port 8080 with CORS and error handling. Data-job module starts independently. Both connect to PostgreSQL.

---

## Phase 3: User Story 1 — Frontend Quick Search (Priority: P1) MVP

**Goal**: Frontend users can search for teams and players via a search box with real-time table filtering and clickable dropdown suggestions.

**Independent Test**: Type "McDavid" or "TOR" in the search box → dropdown shows suggestions AND homepage tables filter. Click suggestion → navigates to detail page.

**Note**: The search endpoint (`GET /api/search/all`) is already implemented in `SearchController.java`. This story ensures it works correctly with the new API module independence.

### Tests for User Story 1

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [x] T015 [US1] Controller test for `GET /api/search/all` in `backend/api/src/test/java/com/whoshot/nhl/api/controller/SearchControllerTest.java` using `@WebMvcTest` with mocked repositories — verify 200 status, response contains both player and team entries, and season auto-detection works when param is null

### Implementation for User Story 1

- [x] T016 [US1] Verify `SearchController` works in new API module by ensuring `PlayerRepository.findAllForSearch()` and `TeamRepository.findAllForSearch()` return data. Test manually: start API, hit `GET /api/search/all` — confirm response contains players and teams.
- [x] T017 [US1] Update `SearchController` in `backend/api/src/main/java/com/whoshot/nhl/api/controller/SearchController.java`: add active season auto-detection when `season` param is null (query `CurrentSeasonRepository.findByIsActiveTrue()`)

**Checkpoint**: Search endpoint returns all players and teams. Frontend search bar shows suggestions and filters tables.

---

## Phase 4: User Story 2 — Homepage Data Display (Priority: P1) MVP

**Goal**: Homepage displays ALL player standings, ALL team standings, ALL players with last-10-games points per game, and ALL teams with last-10-games points percentage.

**Independent Test**: Load homepage → all 4 tables populated with complete data. All players and all 32 teams visible. Points percentage columns filled.

### Tests for User Story 2

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [x] T018 [P] [US2] Unit test for player points per game (last 10) calculation in `backend/data-job/src/test/java/com/whoshot/nhl/datajob/service/StatisticsCalculationServiceTest.java` — test `calculatePlayerPointsPerGameLast10()` with known game log data (FR-019, FR-023)
- [x] T019 [P] [US2] Unit test for team points percentage calculation in `backend/data-job/src/test/java/com/whoshot/nhl/datajob/service/StatisticsCalculationServiceTest.java` — test `calculateTeamPointsPercentageLast10()` with known team game data (FR-020, FR-023)
- [x] T020 [P] [US2] Controller test for `GET /api/players/standings` in `backend/api/src/test/java/com/whoshot/nhl/api/controller/PlayerControllerTest.java` using `@WebMvcTest` with mocked service — verify response structure and 200 status
- [x] T021 [P] [US2] Controller test for `GET /api/teams/standings` in `backend/api/src/test/java/com/whoshot/nhl/api/controller/TeamControllerTest.java` using `@WebMvcTest` with mocked service — verify response structure, 200 status, and that team ordering preserves NHL API standings order (FR-002)

### Implementation for User Story 2

- [x] T022 [P] [US2] Create `PlayerStandingsDto` in `backend/api/src/main/java/com/whoshot/nhl/api/dto/PlayerStandingsDto.java` with fields: playerId, firstName, lastName, fullName, positionCode, teamCode, teamLogoUrl, headshotUrl, gamesPlayed, goals, assists, points, pointsPerGame, plusMinus, pointsPerLastNGames, hot, cold, currentPointStreak, currentPointlessStreak
- [x] T023 [P] [US2] Create `TeamStandingsDto` in `backend/api/src/main/java/com/whoshot/nhl/api/dto/TeamStandingsDto.java` with fields per contracts/api-endpoints.md team standings response
- [x] T024 [US2] Create `PlayerService` in `backend/api/src/main/java/com/whoshot/nhl/api/service/PlayerService.java` with method `getPlayerStandings(season)` that queries `PlayerRepository.findByIdSeasonOrderByPointsDesc(season)`, resolves active season when null, and maps to `PlayerStandingsDto`
- [x] T025 [US2] Create `TeamService` in `backend/api/src/main/java/com/whoshot/nhl/api/service/TeamService.java` with method `getTeamStandings(season)` that queries `TeamRepository.findBySeasonOrderByPointsDesc(season)`, resolves active season when null, and maps to `TeamStandingsDto`. Team ordering MUST preserve the order from NHL API standings endpoint (FR-002)
- [x] T026 [US2] Create `PlayerController` in `backend/api/src/main/java/com/whoshot/nhl/api/controller/PlayerController.java` with `GET /api/players/standings` endpoint returning `List<PlayerStandingsDto>` (FR-003)
- [x] T027 [US2] Create `TeamController` in `backend/api/src/main/java/com/whoshot/nhl/api/controller/TeamController.java` with `GET /api/teams/standings` endpoint returning `List<TeamStandingsDto>` (FR-002)
- [x] T028 [US2] Add `GET /api/players/point-streaks` to `PlayerController` — calls `PlayerRepository.findPlayersWithPointStreaks(season)` and maps to DTO (existing frontend component: `PointStreaksTable.vue`)
- [x] T029 [US2] Add `GET /api/players/hot` to `PlayerController` — calls `PlayerRepository.findHotPlayers(season)` and maps to DTO (existing frontend component: `HottestPlayersTable.vue`)
- [x] T030 [US2] Add `GET /api/teams/win-streaks` to `TeamController` — calls `TeamRepository.findTeamsWithWinStreaks(season)` and maps to DTO (existing frontend component: `TeamWinStreaksTable.vue`)
- [x] T031 [US2] Add `GET /api/teams/loss-streaks` to `TeamController` — calls `TeamRepository.findTeamsWithLossStreaks(season)` and maps to DTO (existing frontend component: `TeamWinStreaksTable.vue`)

**Checkpoint**: Homepage loads with all 4 data tables populated. All players and all 32 teams displayed with points percentage columns.

---

## Phase 5: User Story 3 — Team Detail Pages (Priority: P2)

**Goal**: Users navigate to a team detail page and see standings, recent games, streaks, and roster.

**Independent Test**: Navigate to `/team/TOR` → team stats, last 10 games, streaks, and roster displayed.

### Tests for User Story 3

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [x] T032 [P] [US3] Controller test for `GET /api/teams/{teamCode}` in `backend/api/src/test/java/com/whoshot/nhl/api/controller/TeamControllerTest.java` — verify 200 with team data and roster, 404 for unknown team code
- [x] T033 [P] [US3] Controller test for `GET /api/teams/{teamCode}/game-log` in `backend/api/src/test/java/com/whoshot/nhl/api/controller/TeamControllerTest.java` — verify 200 with game log list

### Implementation for User Story 3

- [x] T034 [P] [US3] Create `TeamDetailDto` in `backend/api/src/main/java/com/whoshot/nhl/api/dto/TeamDetailDto.java` with all Team fields plus `roster` (list of `RosterPlayerDto`: playerId, fullName, positionCode, teamCode, headshotUrl) per contracts/api-endpoints.md
- [x] T035 [P] [US3] Create `TeamGameLogDto` in `backend/api/src/main/java/com/whoshot/nhl/api/dto/TeamGameLogDto.java` with fields: gameId, gameDate, opponentTeamCode, homeGame, goalsFor, goalsAgainst, won, overtimeLoss, gameType, gameNumber
- [x] T036 [US3] Add `getTeamDetail(teamCode, season)` method to `TeamService` in `backend/api/src/main/java/com/whoshot/nhl/api/service/TeamService.java` — queries team data + roster (players with matching teamCode), throws 404 if team not found
- [x] T037 [US3] Add `getTeamGameLog(teamCode, season)` method to `TeamService` — queries `TeamGameRepository` for recent games, maps to `TeamGameLogDto`
- [x] T038 [US3] Add `GET /api/teams/{teamCode}` endpoint to `TeamController` returning `TeamDetailDto` (FR-004)
- [x] T039 [US3] Add `GET /api/teams/{teamCode}/game-log` endpoint to `TeamController` returning `List<TeamGameLogDto>` (FR-004)

**Checkpoint**: Team detail page at `/team/TOR` shows full team stats, recent game results, and player roster.

---

## Phase 6: User Story 4 — Player Detail Pages (Priority: P2)

**Goal**: Users navigate to a player detail page and see season stats, identity info, and recent games.

**Independent Test**: Navigate to `/player/8478402` → player identity, season stats, last 10 games displayed.

### Tests for User Story 4

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [x] T040 [P] [US4] Controller test for `GET /api/players/{playerId}` in `backend/api/src/test/java/com/whoshot/nhl/api/controller/PlayerControllerTest.java` — verify 200 with player data, 404 for unknown player
- [x] T041 [P] [US4] Controller test for `GET /api/players/{playerId}/game-log` in `backend/api/src/test/java/com/whoshot/nhl/api/controller/PlayerControllerTest.java` — verify 200 with game log list

### Implementation for User Story 4

- [x] T042 [P] [US4] Create `PlayerDetailDto` in `backend/api/src/main/java/com/whoshot/nhl/api/dto/PlayerDetailDto.java` with all Player fields including nextGame embedded object per contracts/api-endpoints.md
- [x] T043 [P] [US4] Create `PlayerGameLogDto` in `backend/api/src/main/java/com/whoshot/nhl/api/dto/PlayerGameLogDto.java` with fields: gameId, gameDate, opponentTeamCode, homeGame, goals, assists, points, plusMinus, shots, timeOnIce, gameWon, gameNumber
- [x] T044 [US4] Add `getPlayerDetail(playerId, season)` method to `PlayerService` in `backend/api/src/main/java/com/whoshot/nhl/api/service/PlayerService.java` — queries Player entity, throws 404 if not found
- [x] T045 [US4] Add `getPlayerGameLog(playerId, season)` method to `PlayerService` — queries `GameLogRepository.findByPlayerIdAndSeasonIdOrderByGameNumberAsc(playerId, seasonId)`, maps to `PlayerGameLogDto`
- [x] T046 [US4] Add `GET /api/players/{playerId}` endpoint to `PlayerController` returning `PlayerDetailDto` (FR-005)
- [x] T047 [US4] Add `GET /api/players/{playerId}/game-log` endpoint to `PlayerController` returning `List<PlayerGameLogDto>` (FR-005)

**Checkpoint**: Player detail page at `/player/8478402` shows full player stats, identity info, and game-by-game log.

---

## Phase 7: User Story 5 — Automated Game-Day Data Sync (Priority: P3)

**Goal**: Data-job runs as a long-running daemon with internal hourly scheduling. On game days, it transitions to 1-minute sync during active games, then returns to hourly mode after the last game finishes.

**Independent Test**: Start data-job daemon → verify it performs hourly schedule checks. On a game day → verify it syncs every minute during games and returns to hourly mode after games finish. Verify it runs continuously without crashes (SC-008).

**Note**: Core sync functionality already exists in `DynamicSchedulingService` and `DataSyncService`. Tasks focus on converting from exit-after-sync to long-running daemon, adding hourly scheduling, and ensuring it works with PostgreSQL.

### Tests for User Story 5

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [x] T048 [P] [US5] Unit test for duplicate detection: verify that `DataSyncService.syncPlayers()` does not create duplicate GameLog entries for the same `(playerId, gameId)` — test in `backend/data-job/src/test/java/com/whoshot/nhl/datajob/service/DataSyncServiceTest.java`
- [x] T049 [P] [US5] Unit test for scheduling state transitions: verify `DynamicSchedulingService` transitions correctly between hourly mode → game-time mode (1-min) → back to hourly mode — test in `backend/data-job/src/test/java/com/whoshot/nhl/datajob/service/DynamicSchedulingServiceTest.java`

### Implementation for User Story 5

- [x] T050 [US5] Refactor `DynamicSchedulingService` in `backend/data-job/src/main/java/com/whoshot/nhl/datajob/service/DynamicSchedulingService.java`: convert from exit-after-sync to long-running daemon. Replace `System.exit()` calls with return-to-hourly-scheduling logic. Add internal hourly cron (`@Scheduled(cron = "0 0 * * * *")` or `scheduleAtFixedRate`) that checks for games and transitions to 1-minute sync when games are detected (FR-009, FR-014)
- [x] T051 [US5] Update `DataSyncService` in `backend/data-job/src/main/java/com/whoshot/nhl/datajob/service/DataSyncService.java` to use upsert/merge strategy for GameLog and TeamGame inserts to handle unique constraint violations gracefully (FR-018)
- [x] T052 [US5] Add structured logging to `DataSyncService` for all fetch operations: log success/failure, record counts, and errors (FR-016)
- [x] T053 [US5] Add error handling in `DynamicSchedulingService` to catch NHL API failures and continue with next scheduled sync instead of crashing (FR-017)

**Checkpoint**: Data-job starts and runs continuously. Performs hourly schedule checks. On game day, transitions to 1-minute sync. Returns to hourly after games finish. No duplicates. Logs all operations.

---

## Phase 8: User Story 6 — Initial Season Data Load (Priority: P4)

**Goal**: Admin can trigger a one-time full data load to populate the database with current season stats.

**Independent Test**: Start with empty PostgreSQL database → trigger initial load → verify all 32 teams and ~800 players populated.

### Tests for User Story 6

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [x] T054 [US6] Unit test for `InitialDataLoadService.loadFullSeason()` in `backend/data-job/src/test/java/com/whoshot/nhl/datajob/service/InitialDataLoadServiceTest.java` — verify it calls `NhlApiService` methods for all teams and players, uses upsert operations, and handles API failures gracefully

### Implementation for User Story 6

- [x] T055 [US6] Create `InitialDataLoadService` in `backend/data-job/src/main/java/com/whoshot/nhl/datajob/service/InitialDataLoadService.java` with method `loadFullSeason()` that fetches all teams, all players, and their game logs using existing `NhlApiService` methods. Use upsert operations (FR-018).
- [x] T056 [US6] Add CLI trigger for initial load: add Spring Boot `CommandLineRunner` or `@PostConstruct` with a profile/property flag (e.g., `--initial-load=true` or `spring.profiles.active=initial-load`) in `backend/data-job/src/main/java/com/whoshot/nhl/datajob/config/InitialLoadRunner.java` (FR-015)
- [x] T057 [US6] Add logging for initial load progress: log team count, player count, and total time in `InitialDataLoadService` (FR-016)

**Checkpoint**: Run `mvn spring-boot:run -Dspring-boot.run.arguments="--initial-load=true"` → database populated with all teams and players.

---

## Phase 9: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories

- [ ] T058 [P] Update `backend/api/API_REFERENCE.md` to document all new endpoints per contracts/api-endpoints.md (Constitution Principle II)
- [ ] T059 [P] Update `backend/README.md` with new architecture (two independent apps), PostgreSQL setup, and Docker Compose instructions
- [ ] T060 Delete `backend/data-job/nhl_stats.db` SQLite database file — no longer needed after PostgreSQL migration
- [ ] T061 Run full test suite (`cd backend && mvn test`) and verify all tests pass against PostgreSQL via Testcontainers
- [ ] T062 Run quickstart.md validation: follow all steps in `specs/001-api-data-split/quickstart.md` on a clean setup and verify everything works end-to-end

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion — BLOCKS all user stories
- **User Stories (Phase 3–8)**: All depend on Foundational phase completion
  - US1 (Search) and US2 (Homepage) can proceed in parallel
  - US3 (Team Detail) and US4 (Player Detail) can proceed in parallel
  - US5 (Game-Day Sync) can proceed independently
  - US6 (Initial Load) can proceed independently
- **Polish (Phase 9)**: Depends on all user stories being complete

### User Story Dependencies

- **US1 (Search, P1)**: Independent — existing endpoint, needs test + verification
- **US2 (Homepage, P1)**: Independent — new endpoints for player/team standings
- **US3 (Team Detail, P2)**: Depends on US2 (extends TeamController/TeamService) — independently testable
- **US4 (Player Detail, P2)**: Depends on US2 (extends PlayerController/PlayerService) — independently testable
- **US5 (Game-Day Sync, P3)**: Independent — refactors existing data-job to long-running daemon
- **US6 (Initial Load, P4)**: Independent — new service in data-job module

### Within Each User Story

- Tests MUST be written and FAIL before implementation (Constitution Principle I)
- DTOs before services
- Services before controllers/endpoints
- Core implementation before integration

### Parallel Opportunities

- T003, T004, T005 (POM updates) can run in parallel
- T007, T008, T009 (config updates) can run in parallel
- T010, T011 (unique constraints) can run in parallel
- T018, T019, T020, T021 (US2 tests) can run in parallel
- T022, T023 (US2 DTOs) can run in parallel
- T032, T033 (US3 tests) can run in parallel
- T034, T035 (US3 DTOs) can run in parallel
- T040, T041 (US4 tests) can run in parallel
- T042, T043 (US4 DTOs) can run in parallel
- T048, T049 (US5 tests) can run in parallel
- US1+US2 can proceed in parallel after Phase 2
- US3+US4 can proceed in parallel after Phase 2
- US5+US6 can proceed in parallel after Phase 2

---

## Parallel Example: User Story 2

```bash
# Launch all tests for US2 together (write first, verify they fail):
Task: T018 "Unit test for player points per game (last 10)"
Task: T019 "Unit test for team points percentage calculation"
Task: T020 "Controller test for GET /api/players/standings"
Task: T021 "Controller test for GET /api/teams/standings"

# Launch both DTOs together:
Task: T022 "Create PlayerStandingsDto"
Task: T023 "Create TeamStandingsDto"

# Then services sequentially (depend on DTOs):
Task: T024 "Create PlayerService"
Task: T025 "Create TeamService"

# Then controllers (depend on services):
Task: T026 "Create PlayerController"
Task: T027 "Create TeamController"
```

---

## Implementation Strategy

### MVP First (User Stories 1 + 2 Only)

1. Complete Phase 1: Setup (Docker + PostgreSQL + module independence)
2. Complete Phase 2: Foundational (constraints, CORS, error handling)
3. Complete Phase 3: US1 (Search — test + verify existing endpoint)
4. Complete Phase 4: US2 (Homepage — new player/team standings endpoints)
5. **STOP and VALIDATE**: Homepage shows all data, search works, tables filter
6. Deploy/demo if ready

### Incremental Delivery

1. Setup + Foundational → Both apps run independently on PostgreSQL
2. US1 + US2 → Homepage fully functional (MVP!)
3. US3 + US4 → Detail pages functional
4. US5 → Data-job converted to long-running daemon
5. US6 → Initial load trigger available
6. Polish → Documentation, cleanup, full test validation

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Each user story is independently completable and testable
- Tests must fail before implementing (Constitution Principle I)
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
- Frontend code requires NO changes — API matches existing `useApi.js` contract
