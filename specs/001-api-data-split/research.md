# Research: Backend Modularization - API & Data Job Separation

**Feature Branch**: `001-api-data-split`
**Date**: 2026-03-27

## R-001: PostgreSQL Migration from SQLite

**Decision**: Migrate from SQLite to PostgreSQL 16, running locally via Docker Compose.

**Rationale**: The spec requires two independent applications (API and data-job) accessing the same database concurrently. SQLite is single-writer and the current HikariCP pool is limited to 1 connection. PostgreSQL supports concurrent connections natively, enabling both apps to read/write simultaneously.

**Alternatives considered**:
- **Keep SQLite with WAL mode**: WAL allows concurrent reads but still single writer. Risky when data-job writes during game sync while API serves requests. Rejected for reliability concerns.
- **H2 in server mode**: Possible but PostgreSQL is the industry standard, better documented, and closer to production usage. Rejected for pragmatism.
- **Shared file-based approach**: No concurrent access guarantees. Rejected.

**Migration impact**:
- Replace `sqlite-jdbc` dependency with `postgresql` driver in domain and module POMs
- Replace `SQLiteDialect` with default PostgreSQL dialect (auto-detected by Spring Boot)
- Remove `hikari.maximum-pool-size=1` constraint
- `GenerationType.IDENTITY` works with PostgreSQL (uses `SERIAL`/`BIGSERIAL`)
- Composite keys (`@EmbeddedId`, `@IdClass`) work identically
- `ddl-auto=update` works for dev; Flyway can be added later for production
- Add `docker-compose.yml` with PostgreSQL 16 service

## R-002: API Module Independence

**Decision**: Give the API module its own `@SpringBootApplication` class and remove the dependency on data-job module.

**Rationale**: Currently the API module has no main class — it depends on the data-job module which contains `DataIngestionJob.java` as the entry point. This means the API cannot run independently. The spec requires independent deployment.

**Alternatives considered**:
- **Keep combined deployment**: Simpler but violates the core spec requirement of independent modules. Data-job exits the JVM when no games are scheduled, which would kill the API. Rejected.
- **Single app with profiles**: Use `@Profile` annotations to enable/disable data-job scheduling. Possible but still couples deployment. Rejected for not meeting independence requirement.

**Implementation**:
- Create `ApiApplication.java` in `backend/api/src/main/java/com/whoshot/nhl/api/`
- API module depends only on `domain` module (not `data-job`)
- Scan entities from `com.whoshot.nhl.domain.entity` and repositories from `com.whoshot.nhl.domain.repository`
- API runs on port 8080 (existing), data-job doesn't need a web port

## R-003: Points Percentage Calculation for Last 10 Games

**Decision**: Calculate points percentage in the data-job module during sync and store as denormalized fields on Player and Team entities.

**Rationale**: The spec requires points percentage for last 10 games for both players (FR-019) and teams (FR-020). Calculating at query time would require joining game logs and computing aggregates on every homepage load. Pre-computing during sync is more efficient and aligns with the existing pattern (see `pointsPerLastNGames` field on Player entity, `last10GamesPointPercentage` on Team entity).

**Player points percentage formula**: `total_points_in_last_10_games / (10 * max_points_per_game)`. For players, a player can earn at most ~some variable points per game, but the spec says "total points earned / maximum possible points in those games." Since a player's "maximum possible" isn't well-defined in hockey, we'll use: points earned in last 10 / games played in window (i.e., points per game over last 10), consistent with existing `pointsPerLastNGames` field.

**Team points percentage formula**: `standings_points_earned_in_last_10 / 20` (where 20 = 10 games × 2 points max per game). This matches existing `last10GamesPointPercentage` field and FR-020 definition.

**Alternatives considered**:
- **Calculate at API query time**: More accurate but slower. For homepage serving all players, this would require ~800 subqueries. Rejected for performance.
- **Materialized view**: PostgreSQL supports this but adds complexity. Pre-computed fields are simpler and already in the entity model. Rejected per Principle III.

## R-004: API Endpoint Design

**Decision**: Implement 5 REST endpoints in the API module, aligning with the existing frontend `useApi.js` composable.

**Rationale**: The frontend already defines the expected API contract in `useApi.js`. The endpoints are:

| Frontend Call | API Endpoint | Maps to Spec |
|---------------|-------------|--------------|
| `getTopScorers()` | `GET /api/players/standings` | FR-003 (homepage players) |
| `getPlayerDetails(id)` | `GET /api/players/{playerId}` | FR-005 (player detail) |
| `getPlayerGameLog(id)` | `GET /api/players/{playerId}/game-log` | FR-005 (player detail) |
| `getStandings()` | `GET /api/teams/standings` | FR-002 (homepage teams) |
| `getTeamDetails(code)` | `GET /api/teams/{teamCode}` | FR-004 (team detail) |
| `getTeamGameLog(code)` | `GET /api/teams/{teamCode}/game-log` | FR-004 (team detail) |
| existing search | `GET /api/search/all` | FR-001 (search) |

Additional frontend endpoints (`/players/point-streaks`, `/players/hot`, `/teams/win-streaks`, `/teams/loss-streaks`) are used by existing components. These should be implemented for backward compatibility.

**Alternatives considered**:
- **GraphQL**: More flexible but over-engineered for a personal project with fixed frontend. Rejected per Principle III.
- **BFF pattern**: Unnecessary indirection for a single frontend consumer. Rejected.

## R-005: Data-Job Scheduling Architecture

**Decision**: Convert the data-job to a long-running daemon with internal hourly scheduling.

**Rationale**: The spec requires the data-job to run continuously for 7 days without crashes (SC-008) and perform hourly schedule checks internally (FR-009). The current exit-after-sync design requires an external scheduler, which contradicts SC-008. A long-running daemon with `@Scheduled` or `TaskScheduler` handles hourly checks internally and transitions to 1-minute sync during game windows.

**Implementation**:
- Refactor `DynamicSchedulingService` to remove `System.exit()` calls
- Add hourly scheduled method to check for games via NHL API
- On game days: transition to 1-minute `scheduleAtFixedRate` sync
- After last game finishes: cancel frequent sync, return to hourly mode
- Process stays alive continuously — no external scheduler needed

**Alternatives considered**:
- **Exit-after-sync with external cron** (previous design): Simpler but contradicts SC-008 and requires external scheduler configuration. Rejected per clarification.
- **Spring Batch**: Heavyweight framework for what's essentially "fetch and upsert." Rejected per Principle III.

## R-006: Testing Strategy

**Decision**: Use JUnit 5 with plain unit tests for calculation logic, and Testcontainers for repository/integration tests.

**Rationale**: Constitution Principle I requires test-first development. The spec explicitly requires unit tests for statistics calculations (FR-023). Testcontainers provides a real PostgreSQL instance in tests, avoiding dialect mismatches.

**Test categories**:
- **Unit tests**: `StatisticsCalculationService` (player + team points percentage, streak detection). No Spring context needed.
- **Controller tests**: `@WebMvcTest` with mocked services for endpoint validation.
- **Repository tests**: `@DataJpaTest` with Testcontainers PostgreSQL for query correctness.

**Alternatives considered**:
- **H2 for test database**: Dialect differences can mask bugs. Rejected after user mentioned PostgreSQL migration.
- **Mocked repositories only**: Constitution says no mocking database (based on constitution spirit). Testcontainers is preferred but acceptable to use `@WebMvcTest` with mocked services for controller tests since that's testing HTTP handling, not data access.
