# Contract: Season Backfill Invocation

**Feature**: `002-season-backfill`

The backfill exposes no HTTP interface (spec A-003). Its contract is the command line of the `data-job` application.

## Command

```bash
java -jar data-job.jar \
  --spring.profiles.active=backfill \
  --backfill.season=20242025
```

Under Compose:

```bash
podman-compose run --rm \
  -e SPRING_PROFILES_ACTIVE=backfill \
  -e BACKFILL_SEASON=20242025 \
  data-job
```

## Parameters

| Property | Env var | Required | Default | Meaning |
|---|---|---|---|---|
| `spring.profiles.active` | `SPRING_PROFILES_ACTIVE` | yes | — | must include `backfill` to activate the runner |
| `backfill.season` | `BACKFILL_SEASON` | yes | — | target season, `YYYYYYYY` (e.g. `20242025`) |
| `backfill.request-delay-ms` | `BACKFILL_REQUEST_DELAY_MS` | no | — | **deprecated, ignored** (logs a warning); pacing is per request via `nhle.api.requests.*`, see [`backend/INGESTION.md`](../../../backend/INGESTION.md) |
| `backfill.dry-run` | `BACKFILL_DRY_RUN` | no | `false` | validate and report counts without writing |

Database connection uses the existing `spring.datasource.*` properties; no backfill-specific configuration.

## Behaviour guarantees

- **No writes before validation passes** (FR-002). A rejected season leaves the database untouched.
- **`current_season` is never written** (FR-005). The active-season marker and the site's default view are unaffected (SC-006).
- **Only the requested season is written** (FR-004). Rows for other seasons are neither updated nor deleted.
- **Re-running the same season is a no-op on counts** (FR-006).
- **Live sync may run concurrently** — the backfill of a *completed* season touches disjoint rows. Two backfills of the same season are serialized by an advisory lock (FR-014).

## Exit codes (FR-009)

| Code | Meaning |
|---|---|
| `0` | Season loaded. Individual records may have been skipped; the summary lists them. |
| `1` | Validation failed (missing, malformed, unknown, or not-yet-started season). Nothing written. |
| `2` | Fatal upstream failure — seasons list or standings unavailable after retries. Partial data may be present; re-running is safe. |
| `3` | Another backfill for this season is already running. Nothing written. |

## Output contract (FR-008)

Progress, at INFO, at least every 25 records:

```
Backfill 20242025: phase=players 250/1024 (24%) elapsed=00:04:12
```

Summary, at completion:

```
=== Backfill summary: season 20242025 ===
Duration:        00:18:41
Teams:           32
Team games:      1312
Players:         1021
Player game logs: 41288
Skipped:         3
  - player 8478402: points mismatch (calculated 87, standings 88)
  - player 8471234: upstream 404 after 3 retries
  - team  ARI: no standings row for 2025-04-17
Result:          SUCCESS
```

The skip list is the machine-readable part of FR-007 — every record not loaded appears here with a reason.

## Downstream contract (unchanged)

`API_CONTRACT.md` needs no change. Backfilled data is served by the existing season-scoped endpoints (FR-011):

| Endpoint | Behaviour after backfill |
|---|---|
| `GET /players/{id}/game-log?season=20242025` | returns that season's logs, `game_number` ascending |
| `GET /teams/{code}/game-log?season=20242025` | returns that season's games, date descending |
| `GET /players?season=20242025` | returns that season's standings |
| `GET /teams?season=20242025` | returns that season's final standings |
| any endpoint with no `season` parameter | unchanged — still resolves the active season |

## Upstream endpoints consumed

| Endpoint | Purpose | New to this feature |
|---|---|---|
| `GET api.nhle.com/stats/rest/en/season` | validate the season exists, get `regularSeasonEndDate` | no |
| `GET api-web.nhle.com/v1/standings/{YYYY-MM-DD}` | final standings for the season | **yes** (R-001) |
| `GET api-web.nhle.com/v1/club-schedule-season/{team}/{season}` | team games, scores, outcomes | no (needs extra DTO fields) |
| `GET api-web.nhle.com/v1/skater-stats-leaders/{season}/2?categories=points&limit=-1` | player roster for the season | no |
| `GET api-web.nhle.com/v1/player/{id}/landing` | player identity | no |
| `GET api-web.nhle.com/v1/player/{id}/game-log/{season}/2` | per-game stat lines | no |
