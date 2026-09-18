# Implementation Plan: Historical Season Backfill

**Branch**: `002-season-backfill` | **Date**: 2026-09-13 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/002-season-backfill/spec.md`

## Summary

Add a maintainer-run batch mode to the existing `data-job` application that loads one explicitly named NHL season — teams, team games, players, and per-game player logs — into PostgreSQL, without disturbing the current season or the active-season marker. Invoked as `--spring.profiles.active=backfill --backfill.season=20242025`, mirroring the existing `initial-load` profile.

Research turned up two facts that shape the work:

1. **Historical standings are available** via `GET /v1/standings/{YYYY-MM-DD}` (probed live, `200`, same response shape). Using the season's `regularSeasonEndDate` gives final standings, resolving the spec's main open risk.
2. **Nothing in the codebase writes `game_logs` or `team_games`.** `GameLogRepository` is injected into `DataSyncService` and never used; `TeamGameRepository` is not referenced by `data-job` at all. The game-log endpoints return `[]` for every season, so the game-log graphs the issue is about have no data source at all today. FR-003 forces building that write path here; the plan makes it season-parameterised and wires the live sync to it too, because SC-007 is otherwise unobservable.

The frontend needs no work — `PlayerPage.vue`/`TeamPage.vue` already fetch a previous season and the graph components already take a `previousSeasonData` prop.

## Technical Context

**Language/Version**: Java 23
**Primary Dependencies**: Spring Boot 3.5.6 (Spring Data JPA, Spring Retry, Lombok), PostgreSQL driver
**Storage**: PostgreSQL 16 — existing tables `players`, `teams`, `game_logs`, `team_games`; no schema change
**Testing**: JUnit 5, Mockito, Spring Boot Test, Testcontainers (PostgreSQL)
**Target Platform**: JVM batch process, run locally or via Podman/Docker Compose against a deployed database
**Project Type**: Multi-module Maven backend (`domain`, `api`, `data-job`) + Vue 3 frontend; this feature touches `data-job` and `domain` only
**Performance Goals**: A full season completes unattended in roughly 10–25 minutes (~2100 upstream requests at 100ms pacing). Throughput is not a goal; not getting rate-limited is.
**Constraints**: Bounded memory — stream per player/per team, never hold a whole season at once. No writes before validation. Never write `current_season`. Idempotent re-runs.
**Scale/Scope**: ~1000 skaters, ~32 teams, ~1300 games, ~41k player game logs per season. One season per invocation.

**Deviation from constitution's Technology Standards**: the constitution specifies Spring Boot 4.x; the repository is on 3.5.6 (see `CLAUDE.md` and the constitution's own outstanding follow-up action "Update backend/pom.xml Spring Boot parent to 4.x when upgrading"). This feature follows the repository's actual version. Upgrading the framework is out of scope and would be its own migration.

## Constitution Check

*GATE: evaluated before Phase 0 and re-checked after Phase 1.*

### I. Test-First Development (NON-NEGOTIABLE) — PASS

Every unit of behaviour below gets a failing test before implementation. `/speckit.tasks` must order test tasks ahead of their implementation tasks.

| Behaviour | Test level | Notes |
|---|---|---|
| Season validation: malformed, unknown, not-yet-started, missing | unit | pure; no I/O |
| `gameNumber` assignment, `toi` parsing, home/away orientation, OT-loss derivation | unit | pure mappers |
| Standings date derivation from `regularSeasonEndDate` | unit | |
| Per-player failure isolation and skip-list content | unit, mocked `NhlApiService` | FR-007 |
| `current_season` untouched by a run | integration, Testcontainers | FR-005 / SC-006 |
| Idempotency: run twice, identical counts, no duplicates | integration, Testcontainers | FR-006 / SC-004 |
| Season isolation: other seasons' rows unchanged | integration, Testcontainers | FR-004 |
| Totals equal the sum of loaded game logs | integration, Testcontainers | SC-003 |
| Advisory lock refuses a second concurrent run | integration, Testcontainers | FR-014 |

Two existing testing problems are in scope because this feature makes them worse:
- `NhlApiServiceTest` boots the full context and runs the real `@PostConstruct` sync against the developer's database. R-008 removes the construction-time side effect, which fixes this as a side effect.
- Backend tests require a live PostgreSQL; new integration tests use Testcontainers explicitly rather than relying on an ambient dev database.

### II. Documentation-First — PASS

- [contracts/cli-contract.md](./contracts/cli-contract.md) — invocation, parameters, exit codes, output format, upstream endpoints consumed.
- `API_CONTRACT.md` — **no change required**; backfilled data flows through existing season-scoped endpoints. The plan states this explicitly so the absence of a change is deliberate, not an oversight.
- `backend/README.md` — add a "Backfilling a past season" section (the quickstart is the source text).
- Inline docs on the new services, matching the existing Javadoc density in `data-job`.
- [research.md](./research.md) records the decisions and rejected alternatives.

### III. Pragmatic Architecture — PASS, with two justified items

Reuses what exists: `ApiClient` retry/backoff, `PlayerFactory`, `StatisticsCalculationService`, all four entities, the `CommandLineRunner` + profile pattern from `InitialLoadRunner`. No new dependency, no new table, no new module.

Two items need justification; both are recorded in Complexity Tracking below.

## Project Structure

### Documentation (this feature)

```text
specs/002-season-backfill/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   └── cli-contract.md  # Phase 1 output
├── checklists/
│   └── requirements.md  # From /speckit.specify
└── tasks.md             # /speckit.tasks — not created here
```

### Source Code (repository root)

```text
backend/
├── domain/src/main/java/com/whoshot/nhl/domain/
│   ├── entity/                       # UNCHANGED — Player, Team, GameLog, TeamGame, CurrentSeason
│   └── repository/
│       ├── GameLogRepository.java    # + findByPlayerIdAndGameId
│       └── TeamGameRepository.java   # + findByTeamCodeAndGameId, findBySeasonId
│
├── data-job/src/main/java/com/whoshot/nhl/datajob/
│   ├── config/
│   │   └── BackfillRunner.java       # NEW  @Profile("backfill") CommandLineRunner, exit codes
│   ├── dto/nhlapi/
│   │   └── GameDto.java              # + TeamInfo.score, + gameOutcome.lastPeriodType
│   ├── model/
│   │   ├── BackfillRequest.java      # NEW  validated season + dates + gameType
│   │   └── BackfillSummary.java      # NEW  counts, skip list, success
│   ├── service/
│   │   ├── SeasonBackfillService.java # NEW  orchestrates the 4 phases, isolates per-record failures
│   │   ├── GameLogWriter.java        # NEW  maps + upserts game_logs and team_games (shared with live sync)
│   │   ├── BackfillLockService.java  # NEW  pg_try_advisory_lock guard
│   │   ├── NhlApiService.java        # + getTeamStandings(date), pacing hook
│   │   └── DataSyncService.java      # @PostConstruct side effect removed; season-parameterised methods extracted
│   └── util/
│       └── SeasonValidator.java      # reused; dead getCurrentSeason() removed
│
└── data-job/src/test/java/com/whoshot/nhl/datajob/
    ├── service/                      # unit tests (mocked NhlApiService)
    └── backfill/                     # Testcontainers integration tests

frontend/                             # UNCHANGED — already wired for previous-season comparison
```

**Structure Decision**: Existing multi-module backend layout, unchanged. All new code lands in `data-job` (the batch process), with two additive repository methods in `domain`. No new module: the backfill shares DTOs, entities, the API client, and the statistics logic with the live sync, so splitting it out would duplicate more than it isolates.

## Implementation Phases

Ordered so each phase is independently testable and the P1 user story is deliverable before P2/P3 work.

**Phase A — Season-parameterised foundations (blocks everything)**
Remove the `@PostConstruct` side effect from `DataSyncService` and make the current-season resolution an explicit call from the live-sync startup path (R-008). Extract `applyStandings` and the sync methods so they take a `seasonId` argument rather than reading a field. Add `getTeamStandings(String date)`. Existing behaviour must be unchanged — the existing `DataSyncServiceSeasonResolutionTest` is the regression net.

**Phase B — Validation and entry point (US1, FR-001/002/009)**
`BackfillRequest` + its four-step validation, `BackfillRunner`, exit codes. At the end of this phase the command runs, validates, and reports "nothing to do" without writing.

**Phase C — Teams and team games (US1, FR-003)**
Load final standings from the dated endpoint; extend `GameDto`; write `team_games` from each team's season schedule. First phase that writes data; first observable result via `GET /teams/{code}/game-log?season=…`.

**Phase D — Players and player game logs (US1, FR-003)**
Load skater leaders → landing → game log per player; reuse `PlayerFactory`; write `game_logs` with `gameNumber` and `gameWon` resolved against Phase C. Completes US1 and, with a past season loaded, US2 (SC-007) with no frontend change.

**Phase E — Robustness (US3, FR-006/007/008/013/014)**
Per-record failure isolation and the skip list, progress logging and the summary, request pacing, the advisory lock, and the idempotency integration test.

**Phase F — Live-sync wiring and docs**
Call `GameLogWriter` from the live sync so the current-season line in the graph is populated (R-003), and write the `backend/README.md` section.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Extending scope to write `game_logs`/`team_games` from the **live sync**, not just the backfill (Phase F) | SC-007 says a visitor "sees a previous-season comparison baseline" on the game-log graph. The graph draws current + previous. Nothing writes current-season game logs today, so with backfill alone the graph stays empty and the success criterion is unverifiable through the product. | Backfill-only writes: satisfies FR-003 but ships a feature whose stated outcome can only be confirmed by SQL. Deferring to a separate issue: same code, same call site, but creates an ordering dependency and leaves this feature unobservable in the meantime. |
| Postgres advisory lock (`BackfillLockService`) | FR-014 requires that concurrent runs cannot corrupt data. | A `backfill_runs` status table: a crashed run leaves a permanent `RUNNING` row that blocks every future run until cleared by hand. An advisory lock releases on disconnect. No guard at all: FR-014 is explicit. |

Not treated as complexity: `BackfillRequest`/`BackfillSummary` are plain records in the existing `model` package alongside `PlayerStatistics`; `GameLogWriter` is a service in a codebase that already uses the service-layer pattern.

## Risks

| Risk | Impact | Mitigation |
|---|---|---|
| Refactoring `DataSyncService`'s `@PostConstruct` breaks the live sync | High — the live sync is the running product | Phase A is behaviour-preserving and lands behind the existing season-resolution tests before any new feature code |
| A season's standings row is missing for a relocated franchise | Medium | Recorded in the skip list, run still succeeds; team game logs remain loadable by that season's team code |
| Points-mismatch validation fires often on historical data | Medium — many skipped players | FR-007 makes it non-fatal and lists every one; if the rate is high on a trial run, the validation's applicability to completed seasons gets revisited with real numbers rather than in advance |
| Upstream throttles a full-season run | Medium | Tunable `backfill.request-delay-ms`; existing exponential backoff; re-run is idempotent |

## Next

`/speckit.tasks` — with test tasks ordered before implementation tasks per Principle I, and phases A→F as the dependency order.
