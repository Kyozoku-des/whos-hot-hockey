# Who's Hot - NHL Statistics Backend

A Spring Boot backend that provides REST APIs for NHL statistics with a focus on identifying "hot" and "cold" players and teams based on recent performance.

## Features

- **Team Standings**: View current NHL team standings
- **Player Statistics**: Access comprehensive player stats including points, goals, assists
- **Point Streaks**: Track players with active point streaks
- **Hot Players**: Identify players performing exceptionally well in recent games
- **Win/Loss Streaks**: Monitor teams on winning or losing streaks
- **Individual Pages**: Detailed statistics for specific players and teams

## Technology Stack

- **Framework**: Spring Boot 4.1.1 (Spring Framework 7, Hibernate 7, Jackson 3)
- **Database**: PostgreSQL 17 (via Podman/Docker Compose)
- **Build Tool**: Maven
- **Java Version**: 25 (Eclipse Temurin 25 in the container images)
- **Documentation**: SpringDoc OpenAPI (Swagger UI)

## Module Structure

The backend is organized as a Maven multi-module project with two independent Spring Boot applications:

- **domain** - Shared JPA entities, repositories, and common utilities used by both applications
- **api** - REST API application serving all `/api/**` endpoints (read-only, port 8080)
- **data-job** - NHL data sync daemon that fetches data from the NHL API and writes to PostgreSQL

## Prerequisites

- JDK 25
- Maven 3.9+
- Podman & podman-compose (or Docker & Docker Compose)

## Getting Started

See [database setup and Flyway migrations](DATABASE.md) for schema and connection settings.
See [environment setup](ENVIRONMENTS.md) for local/prod profiles, `.env` files,
rotating local logs, and console-only production logging.
Run Compose commands from the repository root.

### 1. Start All Services (Recommended)

Using Podman:
```bash
podman-compose up -d
```

Or using Docker:
```bash
docker compose up -d
```

This starts PostgreSQL, the API server, data-job daemon, and frontend.

### Alternative: Manual Setup

Follow [local Java process setup](ENVIRONMENTS.md#local-java-processes) for
building and running the API, ongoing sync, and initial data loads.

## API Documentation

See [API_REFERENCE.md](API_REFERENCE.md) for endpoints, season parameters, and example responses.

Once the API application is running, access the interactive API documentation at:

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

## Configuration

Shared settings live in each application's `src/main/resources/application.properties`.
Environment overrides live in `application-local.properties` and `application-prod.properties`.
Use the [environment guide](ENVIRONMENTS.md) for connection settings and logging.
Fetch concurrency, the outbound request budget, retries and the related metrics are described in
the [ingestion guide](INGESTION.md).
The current season is resolved from NHL season dates; it is not a fixed property.

## Data Flow

```
External NHL API
      |
data-job (fetch + sync)
      |
PostgreSQL 17 (persist data)
      |
api (query + serve)
      |
Clients (consume REST API)
```

## Sync schedule

The data-job daemon alternates between two modes:

- **Hourly full sync** – season, all team standings, all team schedules (`team_games`), every
  team's official roster (`team_rosters`) and all active players with their game logs
  (`game_logs`). Players are the skater points leaders plus everyone on a roster, so players
  without points and goalies are stored too. It also reads today's schedule to find the
  first and last game start times.
- **Game-time sync** – from 5 minutes before the first game until the last game has ended, a poll
  runs 60 seconds after the previous one finishes. Each poll:
  1. reads `/v1/schedule/now` to find games in `PRE`, `LIVE` or `CRIT` state;
  2. updates standings rows for teams in those games, plus teams that were playing in the
     previous poll (so final stats are captured once);
  3. for teams whose game has just ended, re-reads their season schedule and writes the completed
     game to `team_games`. A team whose write fails stays pending and is retried every poll;
  4. updates those teams' players and their `game_logs`. Players are selected from the roster the
     last full sync stored in `players`, so players on teams that are not playing cost no API
     calls. Players in the standings with no stored row yet (opening night, season debuts) are
     checked too. A player traded to a playing team since the last full sync is picked up by the
     full sync that closes the game window.

  When the last game has ended, a full sync runs and the daemon returns to hourly checks.

Both modes fetch teams and players in parallel and write them one at a time, committing each
player with their game logs; see the [ingestion guide](INGESTION.md).

Live data comes from the same endpoints as the full sync (standings, skater leaders, team
roster, player landing and game log, club schedule); no boxscore or play-by-play is read.

### No duplicates

Both modes write to the same rows. Every write is an upsert on the table's natural key, so a
poll overwrites what the previous poll or full sync wrote instead of adding rows:

| Table       | Key                     | Enforced by                        |
|-------------|-------------------------|------------------------------------|
| `players`   | `(player_id, season_id)`| primary key                        |
| `teams`     | `(season_id, team_code)`| primary key                        |
| `game_logs` | `(player_id, game_id)`  | preload-then-save in `GameLogWriter`|
| `team_games`| `(team_code, game_id)`  | preload-then-save in `GameLogWriter`|

The two `GameLogWriter` keys have no database constraint. They are safe because every writer of a
season (daemon full syncs and polls, initial load, backfill) holds that season's PostgreSQL
advisory lock while it writes, and each run has a single writer thread.

`team_games` only holds completed games (`OVER`, `FINAL`, `OFF`); an in-progress game is
never written there, so a running score can't be recorded as a result.

### Checking an overnight run

Each step logs one summary line prefixed `[game-sync]`:

```powershell
podman logs nhl-data-job 2>&1 | Select-String "\[game-sync\]"
```

```
[game-sync] Hourly full sync done in 412s; game window opens at 2026-10-04T23:00:00Z (games ...)
[game-sync] Poll #1 in 38s: games [VAN 0-0 EDM PRE]; finished []; updated 2 teams, 0 team schedules, 41 players
[game-sync] Poll #87 in 35s: games []; finished [VAN, EDM]; updated 2 teams, 2 team schedules, 41 players
[game-sync] Game window closed after 87 polls (0 failed); final full sync done in 405s, next check in 1h
```

Failed polls are logged as `[game-sync] Poll #N failed` with the stack trace, and completed games
waiting for a retry as `[game-sync] Poll #N: completed games not yet written for [...]`.
Every sync also logs an `[ingestion]` line with its request rate, retries, throttle wait, database
time and written/skipped counts ([details](INGESTION.md#observing-a-run)).

## Hot Rating Calculation

The "hot rating" for players (`pointsPerLastNGames`) is the points-per-game average over their last 10 games. `GET /api/players/hot` ranks players by it.

## Development

### Run tests

```powershell
mvn test
```

The integration tests (`*IT.java` and `PostgresMigrationTest`) use Testcontainers to boot a
disposable PostgreSQL container (Testcontainers 2.x, managed by Spring Boot), so `mvn test` needs a
running Docker-API-compatible engine. No test calls the NHL API or touches your local database. With Podman on Windows,
point Testcontainers at the machine's named pipe first:

```powershell
$env:DOCKER_HOST = 'npipe:////./pipe/podman-machine-default'
$env:TESTCONTAINERS_RYUK_DISABLED = 'true' # Ryuk can conflict with Podman
mvn test
```

Endpoint tests use HTTP stubs, and schema tests use disposable PostgreSQL. The suite
does not require a running application database or call the public NHL API.

### Build the project

```bash
mvn clean package
```

### Build without tests

```bash
mvn clean package -DskipTests
```

## Backfilling a past season

The regular data-job sync only ever tracks one season (the one it resolves as "current"), so a
past season never loads on its own — which means player/team game-log graphs have no previous-
season comparison line until you load one explicitly.

See [one-off load commands](ENVIRONMENTS.md#local-java-processes) for Java and Compose.
A backfill of a past season can run beside the daemon, but each process has its own request
budget, so split the rate between them ([several processes](INGESTION.md#several-processes)). A
backfill of the current season fails fast while the daemon is writing it, and the daemon skips
its syncs while a backfill holds the season.

How long a full season takes depends mostly on the configured request rate
(`nhle.api.requests.requests-per-second`). It is safe to re-run: it upserts on each table's natural key, so re-running the same season converges without
duplicates, and it never touches the `current_season` table or the site's default view. See
[`specs/002-season-backfill/quickstart.md`](../specs/002-season-backfill/quickstart.md) for the
full command reference, exit codes, and verification steps.

## Security Considerations

- All NHL API endpoints use HTTPS
- CORS is enabled for frontend integration
- No sensitive data is stored in the database

## License

This project is for educational purposes.
