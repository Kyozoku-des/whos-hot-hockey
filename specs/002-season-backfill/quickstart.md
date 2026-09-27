# Quickstart: Backfilling a Past Season

**Feature**: `002-season-backfill`

Loads one completed NHL season — teams, team games, players, per-game player logs — into the database. Use it to get the previous season in place so player and team game-log graphs have a comparison baseline. The regular sync only ever tracks the season it resolves as current, so past seasons never arrive on their own.

## Prerequisites

- PostgreSQL running and reachable (`podman-compose up -d postgres`)
- `data-job` built: `cd backend && mvn clean install`
- Outbound access to `api-web.nhle.com` and `api.nhle.com`

## Run it

```bash
cd backend/data-job
mvn spring-boot:run \
  -Dspring-boot.run.profiles=backfill \
  -Dspring-boot.run.arguments=--backfill.season=20242025
```

Or from the jar:

```bash
java -jar target/data-job-1.0.0-SNAPSHOT.jar \
  --spring.profiles.active=backfill \
  --backfill.season=20242025
```

Or in Compose:

```bash
podman-compose run --rm \
  -e SPRING_PROFILES_ACTIVE=backfill \
  -e BACKFILL_SEASON=20242025 \
  data-job
```

Expect **10–25 minutes** for a full season, deliberately paced to avoid getting throttled. Progress prints every 25 records. Measured for 20242025 on 2026-09-27: 10m35s, 1,667 upstream requests, 0 skipped (T055).

## Check it worked first (dry run)

```bash
java -jar target/data-job-1.0.0-SNAPSHOT.jar \
  --spring.profiles.active=backfill \
  --backfill.season=20242025 \
  --backfill.dry-run=true
```

Validates the season and reports what it *would* write, without touching the database.

## Reading the result

```
=== Backfill summary: season 20242025 ===
Duration:        00:10:35
Teams:           32
Team games:      2624
Players:         815
Player game logs: 46727
Skipped:         1
  - player 8478402: points mismatch (calculated 87, standings 88)
Result:          SUCCESS
```

`SUCCESS` with a non-empty skip list is normal and expected — those records were individually unloadable and everything else went in. Re-running is safe and will retry them.

| Exit code | Meaning |
|---|---|
| 0 | loaded |
| 1 | bad season — nothing written |
| 2 | upstream unavailable — partial data possible, re-run |
| 3 | another backfill for this season is already running |

## Verify

```bash
# Players loaded for the season
psql -h localhost -U whoshot -d nhl_stats \
  -c "SELECT count(*) FROM players WHERE season = '20242025';"

# Season totals match the sum of the game logs (should return 0 rows)
psql -h localhost -U whoshot -d nhl_stats -c "
  SELECT p.player_id, p.points, sum(g.points) AS log_points
  FROM players p JOIN game_logs g
    ON g.player_id = p.player_id AND g.season_id = p.season
  WHERE p.season = '20242025'
  GROUP BY p.player_id, p.points
  HAVING p.points <> sum(g.points);"

# The active season was NOT touched
psql -h localhost -U whoshot -d nhl_stats \
  -c "SELECT season_id, is_active FROM current_season WHERE is_active = true;"
```

Then through the API:

```bash
curl "http://localhost:8080/api/players/8478402/game-log?season=20242025"
curl "http://localhost:8080/api/teams/COL/game-log?season=20242025"
```

And in the browser: open any player or team page. The game-log graph draws the previous season as a second line automatically once that season is loaded — no frontend change or configuration involved.

## Things worth knowing

- **Safe to re-run.** Loading the same season twice changes nothing the second time. An interrupted run is fixed by running it again.
- **Does not change the active season.** Your default view stays on the current season; the backfill never writes `current_season`.
- **Can run while the live sync is running.** They touch different seasons. Two backfills of the *same* season are refused with exit code 3.
- **Regular season only.** No playoffs, no preseason.
- **Skaters only.** Goalies are absent, matching how the current season is loaded.
- **One season per invocation.** For several seasons, run it several times.

## Tuning

| Property | Default | When to change |
|---|---|---|
| `backfill.request-delay-ms` | `100` | Raise if you see repeated upstream failures; lower only on a connection you know tolerates it |
| `backfill.dry-run` | `false` | Set `true` to validate without writing |
