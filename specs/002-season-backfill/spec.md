# Feature Specification: Historical Season Backfill

**Feature Branch**: `002-season-backfill`
**Created**: 2026-09-13
**Status**: Draft
**Input**: GitHub issue #17 — "Script for loading a season": *"Script for loading a given season is needed. It should load player and team stats into the database. This is needed as for example the previous season will never load into the database by the backend. While the previous season is needed for comparison in game log graphs of players and teams."*

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Load a named past season on demand (Priority: P1)

A maintainer of the Who's Hot NHL deployment needs data from a season that the automatic sync will never load, because the automatic sync only ever tracks the one season it resolves as current. The maintainer names a season (e.g. `20242025`), starts the load, and when it finishes the database holds that season's player records, player game logs, and team records alongside the existing current-season data.

**Why this priority**: Without this, no past-season data can ever exist in the database. Every other story in this feature depends on it. On its own it already delivers value: the data becomes queryable through the existing API.

**Independent Test**: Run the load for a completed season against an empty-of-that-season database, then query the existing player and team endpoints with that season identifier and confirm non-empty, correct-looking results.

**Acceptance Scenarios**:

1. **Given** the database contains only the current season's data, **When** the maintainer runs the load for a completed past season, **Then** players, their per-game logs, and teams for that season are persisted and attributed to that season.
2. **Given** a load has completed for season X, **When** the maintainer queries existing player/team data for the current season, **Then** current-season records are unchanged and no record is mis-attributed between seasons.
3. **Given** the maintainer supplies a season identifier that is malformed or does not exist, **When** the load starts, **Then** it stops immediately with a clear message naming the problem, and writes nothing.
4. **Given** a load is running, **When** the maintainer watches its output, **Then** progress is reported (phase in progress, count processed so far, total expected) so a multi-hour run is not silent.

---

### User Story 2 - Compare current form against last season in graphs (Priority: P2)

A site visitor viewing a player's or team's game-log graph wants a reference line for how that player or team performed in the previous season, so they can judge whether current form is hot or cold relative to their own baseline.

**Why this priority**: This is the motivating use case from issue #17, but it is only reachable once P1 has put the data in place. It is also the story that defines *which* fields the backfill must produce — anything the comparison view needs must be loaded, not just the headline totals.

**Independent Test**: With a past season loaded, request a player's and a team's season-over-season comparison data and confirm both seasons' values are returned and are internally consistent (totals match the sum of the per-game logs).

**Acceptance Scenarios**:

1. **Given** season 20242025 has been backfilled and the current season is in progress, **When** a visitor opens a player's game-log graph, **Then** the previous season's comparison values are available for that player.
2. **Given** a player did not play in the backfilled season (rookie, or was not in the league), **When** the visitor opens that player's graph, **Then** the view shows the absence of a comparison baseline rather than an error or a zeroed-out baseline presented as real data.
3. **Given** a team existed in both seasons, **When** the visitor opens the team's game-log graph, **Then** the previous season's team performance is available for comparison.

---

### User Story 3 - Re-run a load safely after failure or for correction (Priority: P3)

A load of a full season touches thousands of upstream records and can take a long time; network failures, rate limiting, or a bad upstream response can interrupt it. The maintainer needs to re-run the same season without producing duplicates and without having to wipe the database first.

**Why this priority**: Makes the capability operationally usable rather than a one-shot gamble, but the feature delivers value before this exists.

**Independent Test**: Run the load for a season twice in a row and confirm record counts are identical after the second run and no duplicate player/team/game-log rows exist for that season.

**Acceptance Scenarios**:

1. **Given** a season has already been fully loaded, **When** the maintainer runs the load again for the same season, **Then** existing records are updated in place and the total record count for that season is unchanged.
2. **Given** a previous load was interrupted partway through, **When** the maintainer re-runs it, **Then** the load completes the season and the result is indistinguishable from an uninterrupted run.
3. **Given** the upstream data source fails transiently for one player, **When** the load encounters that failure, **Then** it retries, and if the retries are exhausted it records the failure, continues with the remaining players, and reports the list of skipped records in the final summary.

---

### Edge Cases

- **Season that has not started**: a load requested for a future or not-yet-started season must be rejected up front rather than writing an empty season.
- **Season in progress**: loading the season that the automatic sync already owns is permitted but must not fight with it — the result must be consistent, and the "which season is active" marker must not be changed by a backfill.
- **Season predating available upstream data**: a season that the upstream source has no player-level detail for must fail with a clear message, not a silent partial load.
- **Historical team standings**: team standings reflect a point in time. A backfill of a completed season must capture that season's *final* standings, not today's standings labelled with an old season identifier.
- **Teams that no longer exist or have been renamed/relocated**: a team present in the past season but absent today must still be loaded under the identity it had that season.
- **Players who changed teams mid-season or have since retired**: these must be loaded for the backfilled season even though they are inactive today; "inactive today" must not be a reason to omit a player from a past season.
- **Two loads at once**: a second load started while one is already running must not corrupt data — either it is refused, or it is safely serialized.
- **Very large loads**: memory and upstream request volume must stay bounded; the load must not require holding the entire season in memory at once.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: A maintainer MUST be able to start a data load for one explicitly specified season, independent of which season the automatic sync considers current.
- **FR-002**: The system MUST validate the requested season identifier before any write occurs, rejecting malformed identifiers, non-existent seasons, and seasons that have not yet started, with a message that states which check failed.
- **FR-003**: The load MUST persist, for the requested season: each player who recorded regular-season activity, that player's per-game log entries, that player's derived season statistics, and each team with its end-of-season standings values.
- **FR-004**: All records written MUST be attributed to the requested season, and MUST NOT overwrite, delete, or re-attribute records belonging to any other season.
- **FR-005**: The load MUST NOT change which season is marked active for the application, so that the site's default view is unaffected by a backfill.
- **FR-006**: The load MUST be idempotent: running it repeatedly for the same season MUST converge on the same data with no duplicate records.
- **FR-007**: The load MUST retry transient upstream failures, and on permanent failure for an individual record MUST skip that record, continue the run, and include it in a final summary of what was skipped and why.
- **FR-008**: The load MUST report progress while running and a summary on completion covering: season loaded, counts of players / game logs / teams written, count skipped, and total duration.
- **FR-009**: The load MUST exit with a clear success/failure signal that a maintainer or an automated caller can act on.
- **FR-010**: The load MUST be runnable against a deployed environment without requiring the regular automatic sync to be stopped or the application to be redeployed.
- **FR-011**: Loaded historical data MUST be readable through the existing season-scoped data access used by player and team views, with no change required by the consumer beyond naming the season.
- **FR-012**: Player and team game-log views MUST be able to present a previous-season comparison baseline when that season has been loaded, and MUST distinguish "no baseline exists for this player/team" from "baseline is zero".
- **FR-013**: The load MUST respect upstream rate limits, pacing its requests so that a full-season run does not get the deployment throttled or blocked.
- **FR-014**: Concurrent loads MUST NOT be able to corrupt data — a load started while another is in progress is either refused or serialized behind it.

### Key Entities

- **Season**: the unit being loaded; identified by a season identifier (e.g. `20242025`) and bounded by a start date and a regular-season end date. Distinct from the *active* season marker, which the backfill must not modify.
- **Player (per season)**: a player's identity plus their statistics for one specific season. The same person appears once per season they played.
- **Player Game Log Entry**: one game's statistics for one player in one season; the source from which season-level and rolling ("hot") statistics are derived, and the data behind the game-log graph.
- **Team (per season)**: a team's identity plus its standings and derived performance values for one specific season, reflecting that season's final state once the season is complete.
- **Load Run Summary**: the outcome of one backfill execution — season requested, start and end time, counts written, records skipped with reasons, and overall success or failure.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A maintainer can load a complete past season with a single command and no manual data preparation or database editing.
- **SC-002**: After loading a completed season, every team that played that season is present, and the number of players present matches the upstream scoring leaders list for that season to within the documented skip list.
- **SC-003**: For every loaded player, the season totals equal the sum of that player's loaded per-game logs (100% internal consistency, verifiable by query).
- **SC-004**: Running the same season load twice produces zero net change on the second run — identical record counts and no duplicates.
- **SC-005**: A full-season load completes unattended and reports its outcome; a run interrupted by transient upstream failures still finishes and names every record it could not load.
- **SC-006**: Current-season data and the site's default view are provably unaffected by a backfill — record counts and the active-season marker for the current season are identical before and after.
- **SC-007**: Once a previous season is loaded, a visitor viewing any player or team game-log graph sees a previous-season comparison baseline for every player or team that played in both seasons.

## Assumptions

- **A-001**: The season identifier format follows the existing convention already used throughout the system (`YYYYYYYY`, e.g. `20242025`).
- **A-002**: Scope is regular-season data only, matching what the existing automatic sync loads. Playoffs and preseason are out of scope.
- **A-003**: The load is a maintainer/operator action, not an end-user-facing one. It runs from the operator's environment or the deployment host; no UI, no public endpoint, and therefore no new end-user permission model is introduced.
- **A-004**: The data written for a backfilled season uses the same shape and meaning as the data the automatic sync writes, so existing consumers need no change.
- **A-005**: Upstream historical data for recent completed seasons (at minimum the immediately preceding season) is available at the same level of detail as current-season data. Per **FR-002** / the edge cases, seasons where this does not hold fail loudly.
- **A-006**: The comparison baseline in **FR-012** / **SC-007** means the immediately preceding season relative to the season being viewed.
- **A-007**: A full-season load is expected to take a long time (tens of minutes to hours) because of upstream rate limits; this is acceptable for a maintainer-run batch operation, which is why progress reporting (**FR-008**) is required rather than a latency target.

## Out of Scope

- Loading playoff, preseason, or all-star data.
- A user interface or public API for triggering loads.
- Automatic detection and backfill of missing seasons without a maintainer asking for it.
- Bulk loading of the entire league history; the feature loads one named season per invocation (a maintainer may invoke it repeatedly).
- Changing how the current season is synced.

## Dependencies

- An upstream NHL data source that serves per-season player standings, player information, player game logs, and team standings for the requested historical season.
- A reachable database with the existing schema; season-scoped uniqueness on player and team records is what makes **FR-006** achievable.
