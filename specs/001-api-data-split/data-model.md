# Data Model: Backend Modularization - API & Data Job Separation

**Feature Branch**: `001-api-data-split`
**Date**: 2026-03-27

## Entity Overview

All entities exist in the shared `domain` module (`com.whoshot.nhl.domain.entity`). The data-job writes to them; the API reads from them. Both applications share the same PostgreSQL database.

```
┌──────────────────┐     ┌──────────────────┐
│     Player       │────→│      Team        │  (via teamCode + season)
│  PK: playerId    │     │  PK: teamCode    │
│      season      │     │      season      │
└──────┬───────────┘     └──────┬───────────┘
       │                        │
       │ 1:N                    │ 1:N
       ▼                        ▼
┌──────────────────┐     ┌──────────────────┐
│    GameLog       │     │    TeamGame      │
│  PK: id (auto)   │     │  PK: id (auto)   │
│  FK: playerId    │     │  FK: teamCode    │
│      gameId      │     │      gameId      │
└──────────────────┘     └──────────────────┘

┌──────────────────┐
│  CurrentSeason   │
│  PK: id (auto)   │
│  seasonId        │
└──────────────────┘
```

## Entities (Existing — No Schema Changes Required)

The existing entity model already supports all spec requirements. The PostgreSQL migration only changes the underlying database engine, not the JPA entity definitions.

### Player

**Table**: `players`
**Composite PK**: `(player_id, season)`

| Field | Type | Nullable | Spec Mapping |
|-------|------|----------|-------------|
| player_id | BIGINT | NO | Player identifier from NHL API |
| season | VARCHAR(8) | NO | Season scope (e.g., "20252026") |
| first_name | VARCHAR | NO | FR-005: player detail bio |
| last_name | VARCHAR | NO | FR-005: player detail bio |
| full_name | VARCHAR | YES | FR-003: homepage display, FR-001: search |
| position_code | VARCHAR | YES | FR-005: player detail bio |
| team_code | VARCHAR | YES | FR-003: homepage display, links to Team |
| team_logo_url | VARCHAR | YES | Display purposes |
| games_played | INT | YES | FR-003, FR-005: season stats |
| goals | INT | YES | FR-003, FR-005: season stats |
| assists | INT | YES | FR-003, FR-005: season stats |
| points | INT | YES | FR-003, FR-005: season stats |
| points_per_game | DOUBLE | YES | FR-005: derived stat |
| plus_minus | INT | YES | FR-005: season stats |
| penalty_minutes | INT | YES | Unused (retained for data completeness) |
| power_play_goals | INT | YES | Unused |
| shorthanded_goals | INT | YES | Unused |
| game_winning_goals | INT | YES | Unused |
| overtime_goals | INT | YES | Unused |
| shots | INT | YES | Unused |
| shooting_percentage | DOUBLE | YES | Unused |
| current_point_streak | INT | YES | FR-005: streak info |
| current_pointless_streak | INT | YES | FR-005: streak info |
| points_per_last_n_games | DOUBLE | YES | FR-019: last 10 games points percentage |
| hot | BOOLEAN | YES | Display flag |
| cold | BOOLEAN | YES | Display flag |
| last_updated | TIMESTAMP | YES | Cache freshness |
| headshot_url | VARCHAR | YES | Display purposes |
| date (next_game) | VARCHAR | YES | FR-005: next game info |
| opponent_abbrev (next_game) | VARCHAR | YES | FR-005: next game info |
| home_road_flag (next_game) | VARCHAR | YES | FR-005: next game info |

### Team

**Table**: `teams`
**Composite PK**: `(team_code, season)` via `@IdClass`

| Field | Type | Nullable | Spec Mapping |
|-------|------|----------|-------------|
| team_code | VARCHAR | NO | FR-002: homepage, FR-004: detail, FR-001: search |
| season | VARCHAR | NO | Season scope |
| team_name | VARCHAR | NO | FR-002: homepage display |
| franchise_name | VARCHAR | YES | FR-004: detail |
| logo_url | VARCHAR | YES | Display purposes |
| games_played | INT | YES | FR-002, FR-004: stats |
| wins | INT | YES | FR-002: homepage standings |
| losses | INT | YES | FR-002: homepage standings |
| overtime_losses | INT | YES | FR-002, FR-022: OT losses |
| points | INT | YES | FR-002, FR-022: standings points |
| point_percentage | DOUBLE | YES | FR-002: season-level stat |
| goals_for | INT | YES | FR-004: detail stats |
| goals_against | INT | YES | FR-004: detail stats |
| goal_differential | INT | YES | FR-004: detail stats |
| conference_name | VARCHAR | YES | FR-004: standings context |
| division_name | VARCHAR | YES | FR-004: standings context |
| current_win_streak | INT | YES | FR-004: streak info |
| current_loss_streak | INT | YES | FR-004: streak info |
| last10_games_win_percentage | DOUBLE | YES | FR-020: last 10 win % |
| last10_games_point_percentage | DOUBLE | YES | FR-020: last 10 points % |
| last10_games_ppg | DOUBLE | YES | Points per game over last 10 |
| hot | BOOLEAN | YES | Display flag |
| cold | BOOLEAN | YES | Display flag |
| point_streak | BOOLEAN | YES | Consecutive point games flag |
| next_opponent_code | VARCHAR | YES | Next game info |
| next_game_date | VARCHAR | YES | Next game info |
| next_game_is_home | BOOLEAN | YES | Next game info |
| last_updated | VARCHAR | YES | Cache freshness |

### GameLog

**Table**: `game_logs`
**PK**: `id` (auto-generated)

| Field | Type | Nullable | Spec Mapping |
|-------|------|----------|-------------|
| id | BIGINT | NO | Auto-generated |
| player_id | BIGINT | NO | Links to Player |
| game_id | BIGINT | NO | NHL game identifier, FR-013: duplicate detection key |
| game_date | VARCHAR | NO | FR-005: recent games display |
| opponent_team_code | VARCHAR | YES | FR-005: game opponent |
| home_game | BOOLEAN | YES | FR-005: home/away indicator |
| goals | INT | YES | FR-005: per-game stats |
| assists | INT | YES | FR-005: per-game stats |
| points | INT | YES | FR-005, FR-019: per-game stats |
| plus_minus | INT | YES | FR-005: per-game stats |
| shots | INT | YES | FR-005: per-game stats |
| time_on_ice | INT | YES | FR-005: per-game stats (seconds) |
| game_won | BOOLEAN | YES | Win/loss indicator |
| season_id | VARCHAR | YES | Season scope |
| game_number | INT | YES | Ordering within season |

**Duplicate detection** (FR-013): Unique constraint on `(player_id, game_id)` prevents the same game log from being inserted twice. Data-job uses upsert logic.

### TeamGame

**Table**: `team_games`
**PK**: `id` (auto-generated)

| Field | Type | Nullable | Spec Mapping |
|-------|------|----------|-------------|
| id | BIGINT | NO | Auto-generated |
| game_id | BIGINT | NO | NHL game identifier, FR-013: duplicate detection key |
| team_code | VARCHAR | NO | Links to Team |
| game_date | VARCHAR | NO | FR-004: recent games |
| opponent_team_code | VARCHAR | YES | FR-004: game opponent |
| home_game | BOOLEAN | YES | FR-004: home/away |
| goals_for | INT | YES | FR-004: game score |
| goals_against | INT | YES | FR-004: game score |
| won | BOOLEAN | YES | FR-004, FR-020: result |
| overtime_loss | BOOLEAN | YES | FR-004, FR-022: OT loss tracking |
| game_type | VARCHAR | YES | Regular season / playoffs |
| season_id | VARCHAR | YES | Season scope |
| game_number | INT | YES | Ordering within season |

**Duplicate detection** (FR-013): Unique constraint on `(team_code, game_id)` prevents duplicate team game records.

### CurrentSeason

**Table**: `current_season`
**PK**: `id` (auto-generated)

| Field | Type | Nullable | Spec Mapping |
|-------|------|----------|-------------|
| id | BIGINT | NO | Auto-generated |
| season_id | VARCHAR | YES | NHL season identifier |
| season_display_name | VARCHAR | YES | Human-readable name |
| is_active | BOOLEAN | YES | Whether this is the current season |
| last_updated | TIMESTAMP | YES | Last sync time |

### SearchResult (Projection)

Not a table — a read-only projection class used by repository queries for the search endpoint (FR-001).

| Field | Type | Spec Mapping |
|-------|------|-------------|
| type | String | "PLAYER" or "TEAM" |
| id | String | Player ID or team code |
| name | String | Full name or team name |
| secondary_info | String | Team code (players) or conference (teams) |
| team_code | String | Team association |
| image_url | String | Headshot or logo URL |
| season | String | Season scope |

## Validation Rules

- `player_id` and `season` must be non-null (composite PK)
- `team_code` and `season` must be non-null (composite PK)
- `game_id` must be non-null on GameLog and TeamGame
- Points percentage values must be between 0.0 and 1.0 (or equivalent scale)
- `games_played` must be >= 0
- NHL API data is validated before persistence (FR-026)

## State Transitions

### Data-Job Sync States

```
IDLE → CHECKING_SCHEDULE → NO_GAMES_TODAY → SYNC_ONCE → EXIT
                         → GAMES_SCHEDULED → WAITING_FOR_GAME_START
                         → FREQUENT_SYNC (1min) → GAMES_FINISHED → EXIT
```

### Game States (from NHL API)

```
SCHEDULED → IN_PROGRESS → FINAL
                        → FINAL_OT
                        → FINAL_SO
```

The data-job uses `GameState` enum to determine when to fetch and when to stop.
