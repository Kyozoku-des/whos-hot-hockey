# Feature Specification: Backend Modularization - API & Data Job Separation

**Feature Branch**: `001-api-data-split`
**Created**: 2026-03-08
**Status**: Draft
**Input**: User description: "I have started re-factoring of my backend. I want the main app to be the "api" module that feeds the frontend. It should have one endpoint for quick searches of teams and players. One endpoint for fetching the minimum data of all teams that are viewed directly on the first page of the web app. One endpoint for fetching the minimun data for all players that are viewed on the first page of the web app. One (or more if necessary) endpoint to fetch all data needed for team page in web app. One (or more if necessary) endpoint to fetch all data needed for player page in web app. I want the "data-job" module be it's own application that every hour checks if there are any upcoming games (in case game schedule changes). If there are scheduled games for the current day it should prepare to fetch data during these game times untill the last game of the day has finished. It should only fetch data for the players and teams that are involved in the currently ongoing games. It also needs to make sure it does not add duplicates of data when running fetches (for example accidentally add a goal to a team twice). The data-job application should also have some trigger for loading all the teams and players data once to the database in case the current NHL season has been ongoing and we want to load current statistics. I want tests for the statistics calculations so I can verify them."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Frontend Quick Search (Priority: P1)

Frontend users can search for teams and players by typing in a search box, which simultaneously filters the homepage tables in real-time and displays clickable suggestions in the search bar dropdown for quick navigation to detail pages. When searching for a team, the player table filters to show only players from that team.

**Why this priority**: Search is the primary discovery mechanism for users wanting to find specific teams or players. The dual-mode search (table filtering + suggestions) provides both browsing and direct navigation capabilities without requiring separate interfaces.

**Independent Test**: Can be fully tested by entering a search query (e.g., "McDavid" or "TOR") and verifying that: (1) homepage tables filter to show only matching rows, (2) search dropdown shows clickable suggestions, and (3) clicking suggestions navigates to detail pages. Delivers immediate value as both a filter and navigation tool without requiring other features.

**Acceptance Scenarios**:

1. **Given** the frontend homepage is loaded, **When** a user types "McDavid" in the search box, **Then** the search dropdown displays Connor McDavid as a clickable suggestion AND the homepage player tables filter to show only rows containing "McDavid"
2. **Given** the frontend homepage is loaded, **When** a user types "TOR", **Then** the search dropdown displays Toronto Maple Leafs as a clickable suggestion AND the homepage tables filter to show only Toronto team rows AND only players who play for Toronto
3. **Given** the user is typing in the search box, **When** a user types a partial name like "Mac", **Then** both the search suggestions and filtered table rows update in real-time to show all matching players and teams (e.g., MacKinnon, McDavid)
4. **Given** a user clicks a player suggestion in the search dropdown, **When** the user clicks "Connor McDavid", **Then** the browser navigates to the player detail page for Connor McDavid
5. **Given** a user clicks a team suggestion in the search dropdown, **When** the user clicks "Toronto Maple Leafs", **Then** the browser navigates to the team detail page for Toronto
6. **Given** no matching results exist, **When** a user searches for "ZZZZZ", **Then** the search dropdown shows "No results found" AND the homepage tables are empty (all rows filtered out)

---

### User Story 2 - Homepage Data Display (Priority: P1)

Frontend displays comprehensive NHL statistics on the homepage: all player standings, all team standings, all players with points percentage for last 10 games, and all teams with points percentage for last 10 games. This provides immediate value to users visiting the site.

**Why this priority**: The homepage is the first impression and primary use case. Users need to see complete current statistics immediately without pagination or manual searching. This is the core value proposition of the application.

**Independent Test**: Can be fully tested by loading the homepage and verifying that all tables (player standings, team standings, player last-10-games percentages, team last-10-games percentages) populate with complete current-season data. Delivers standalone value as a comprehensive statistics dashboard.

**Acceptance Scenarios**:

1. **Given** the database contains current season data, **When** the homepage loads, **Then** ALL players are displayed in the player standings table with name, team, games played, goals, assists, and points (sortable columns)
2. **Given** the database contains current season data, **When** the homepage loads, **Then** ALL 32 NHL teams are displayed in the team standings table with wins, losses, points, and rank
3. **Given** the database contains current season data, **When** the homepage loads, **Then** ALL players are displayed with their points percentage for the last 10 games (name, team, points in last 10 games, points percentage)
4. **Given** the database contains current season data, **When** the homepage loads, **Then** ALL 32 teams are displayed with their points percentage for the last 10 games (team name, wins/losses in last 10, points earned, points percentage)
5. **Given** the homepage is loaded, **When** all data endpoints respond successfully, **Then** the page fully renders within 2 seconds with all tables populated

---

### User Story 3 - Team Detail Pages (Priority: P2)

Users can view comprehensive statistics for any NHL team including current standings, recent games, win/loss streaks, and roster information.

**Why this priority**: After discovering teams via homepage or search, users need detailed information. This completes the team browsing experience.

**Independent Test**: Can be fully tested by navigating to a team page (e.g., `/team/TOR`) and verifying all team statistics, standings position, recent games, and roster are displayed. Delivers standalone value as a team information page.

**Acceptance Scenarios**:

1. **Given** a team code (e.g., "TOR"), **When** the team page loads, **Then** the system displays current season standings (wins, losses, OT losses, points, rank)
2. **Given** a team code, **When** the team page loads, **Then** the system displays the last 10 games with results (opponent, score, date, home/away)
3. **Given** a team code, **When** the team page loads, **Then** the system displays active win or loss streaks if the team is currently on one
4. **Given** a team code, **When** the team page loads, **Then** the system displays the team roster with player names and positions

---

### User Story 4 - Player Detail Pages (Priority: P2)

Users can view comprehensive statistics for any NHL player including current season stats, career summary, recent games, and performance trends.

**Why this priority**: After discovering players via homepage or search, users need detailed information. This completes the player browsing experience.

**Independent Test**: Can be fully tested by navigating to a player page (e.g., `/player/8478402`) and verifying all player statistics, recent games, and trends are displayed. Delivers standalone value as a player information page.

**Acceptance Scenarios**:

1. **Given** a player ID, **When** the player page loads, **Then** the system displays current season statistics (games, goals, assists, points, plus/minus, points per game, streaks)
2. **Given** a player ID, **When** the player page loads, **Then** the system displays player identity information (full name, position, team, headshot, next game)
3. **Given** a player ID, **When** the player page loads, **Then** the system displays the last 10 games with game-by-game statistics (date, opponent, goals, assists, points, TOI)

---

### User Story 5 - Automated Game-Day Data Sync (Priority: P3)

The system automatically detects game days and fetches updated statistics for teams and players involved in active games, ensuring real-time accuracy during NHL game nights.

**Why this priority**: While valuable for keeping data fresh, the application can function with daily updates. Real-time updates enhance the experience but aren't critical for MVP functionality.

**Independent Test**: Can be fully tested by simulating a game day scenario: schedule a game for today, verify the system detects it, and confirm that player/team statistics are updated after the game progresses. Delivers standalone value as an automated freshness mechanism.

**Acceptance Scenarios**:

1. **Given** it is 10:00 AM on a game day, **When** the hourly check runs, **Then** the system detects games scheduled for today and prepares to fetch data during game times
2. **Given** a game is currently in progress, **When** the data sync runs, **Then** the system fetches updated statistics only for teams and players involved in active games
3. **Given** updated statistics are fetched for a player, **When** the same goal is reported in a subsequent fetch, **Then** the system detects the duplicate and does not increment the goal count again
4. **Given** the last game of the day has finished, **When** the data sync runs, **Then** the system returns to hourly schedule-check mode and stops frequent fetching
5. **Given** no games are scheduled for today, **When** the hourly check runs, **Then** the system does not initiate game-time data fetching

---

### User Story 6 - Initial Season Data Load (Priority: P4)

Administrators can trigger a one-time full data load to populate the database with all current-season statistics when joining mid-season or initializing a fresh database.

**Why this priority**: This is a one-time setup operation needed only when first deploying or after database resets. Not part of normal user workflows.

**Independent Test**: Can be fully tested by starting with an empty database, triggering the initial load, and verifying that all teams, players, and current-season statistics are populated. Delivers standalone value as a database initialization tool.

**Acceptance Scenarios**:

1. **Given** an empty or outdated database, **When** the initial load trigger is activated, **Then** the system fetches current season data for all 32 NHL teams
2. **Given** the initial load is triggered, **When** the process runs, **Then** the system fetches current season statistics for all active NHL players
3. **Given** the initial load is running, **When** the process completes, **Then** all teams and players have up-to-date statistics reflecting the current season state
4. **Given** the initial load completes successfully, **When** the regular hourly sync resumes, **Then** only incremental updates are fetched rather than full reloads

---

### Edge Cases

- What happens when the NHL API is unavailable or returns errors during a scheduled data fetch? System should log the error, skip the update cycle, and retry on the next scheduled interval without crashing.
- What happens when a search query matches hundreds of players (e.g., searching "a")? The frontend search store limits displayed suggestions to the top 5 matches ranked by name relevance. The full dataset is loaded once and filtered client-side, so large match sets do not trigger additional API calls.
- What happens when a team or player page is requested for an ID that doesn't exist in the database? System should return a 404 error with user-friendly messaging.
- What happens when duplicate detection logic fails and the same goal is added twice? Statistics will be incorrect until the next full data sync corrects the values. System should log duplicate detection failures for monitoring.
- What happens when the initial data load is triggered while the database already has current season data? System should perform an upsert operation (update existing, insert new) rather than creating duplicates.
- What happens when a game goes into overtime or shootout affecting multiple statistics? System should handle all game states (regulation, OT, SO) and correctly update wins, OT losses, and individual player stats.
- What happens when the data-job module is down during a game day? Statistics will become stale until the module is restarted. Frontend should continue serving cached data without errors.

## Clarifications

### Session 2026-03-29

- Q: How is team rank determined when teams have equal points? → A: Rank matches the order returned by NHL API `/v1/standings/now` endpoint, which applies official tie-breaker rules. The data-job stores teams in this order and the API preserves it.
- Q: Should FR-005 include career stats and bio data not currently in the Player entity? → A: No. FR-005 scoped to identity info, current season stats, and recent games only. Career stats and extended bio are deferred.
- Q: Should the data-job be a long-running daemon or exit-after-sync process? → A: Long-running daemon with internal hourly scheduling. Must run continuously for 7 days without crashes (SC-008).
- Q: Should team roster on detail page include jersey numbers? → A: No. Roster shows player names and positions only. Jersey numbers deferred.
- Q: How is player "points percentage" for last 10 games calculated? → A: Points per game over last 10 games (total points / games played in window). Not a percentage — a PPG metric.

## Requirements *(mandatory)*

### Functional Requirements

#### API Module Requirements

- **FR-001**: API module MUST expose a search endpoint that returns all teams and players for the active season, enabling client-side filtering and search suggestions in the frontend dropdown
- **FR-002**: API module MUST expose an endpoint that returns minimal team data for homepage display including ALL 32 teams with their season statistics (team code, name, wins, losses, points, rank) and their points percentage for the last 10 games. Team ordering and rank MUST match the order returned by the NHL API standings endpoint (`/v1/standings/now`), which applies official tie-breaker rules
- **FR-003**: API module MUST expose an endpoint that returns minimal player data for homepage display including ALL active players with their season statistics (name, team, games, goals, assists, points) and their points percentage for the last 10 games
- **FR-004**: API module MUST expose an endpoint that returns complete team data for team detail pages (standings, roster, recent games, statistics)
- **FR-005**: API module MUST expose an endpoint that returns complete player data for player detail pages (identity info, current season stats, recent games)
- **FR-006**: API module MUST respond to all requests within 500ms for cached data and 2 seconds for database queries under normal load
- **FR-007**: API module MUST validate all input parameters and return appropriate HTTP error codes (400 for bad requests, 404 for not found, 500 for server errors)
- **FR-008**: API module MUST support CORS for frontend requests from the configured frontend domain

#### Data Job Module Requirements

- **FR-009**: Data-job module MUST run as a long-running daemon and perform an hourly check to determine if any NHL games are scheduled for the current day
- **FR-010**: Data-job module MUST fetch the current day's game schedule from the NHL API during each hourly check
- **FR-011**: Data-job module MUST transition to game-time mode when games are scheduled for the current day, fetching updated statistics every 1 minute during active games
- **FR-012**: Data-job module MUST fetch updated statistics only for teams and players participating in currently active games
- **FR-013**: Data-job module MUST implement duplicate detection to prevent adding the same statistical event multiple times (e.g., same goal added twice)
- **FR-014**: Data-job module MUST return to hourly schedule-check mode after the last game of the day has concluded
- **FR-015**: Data-job module MUST provide a trigger mechanism for initial full season data load
- **FR-016**: Data-job module MUST log all data fetch operations including success/failure status, records updated, and any errors encountered
- **FR-017**: Data-job module MUST handle NHL API failures gracefully by logging errors and continuing with the next scheduled operation
- **FR-018**: Data-job module MUST use upsert operations to prevent duplicate database records during data synchronization

#### Statistics Calculation Requirements

- **FR-019**: System MUST calculate points per game for players over their last 10 games (total points earned / games played in window)
- **FR-020**: System MUST calculate points percentage for teams over their last 10 games (total standings points earned / maximum possible points, where max = 20 points for 10 wins)
- **FR-021**: System MUST maintain accurate cumulative season statistics (goals, assists, points, games played) for all players
- **FR-022**: System MUST maintain accurate team standings with points calculated as (wins × 2) + (OT losses × 1)
- **FR-023**: All statistics calculations MUST have unit tests verifying correctness with known test data

#### Data Integrity Requirements

- **FR-025**: System MUST ensure database transactions are atomic to prevent partial updates during data synchronization
- **FR-026**: System MUST validate all data received from NHL API before persisting to database
- **FR-027**: System MUST maintain referential integrity between players and teams, games and teams, and statistics and players

### Key Entities

- **Team**: Represents an NHL team with attributes including team code, name, conference, division, current season statistics (wins, losses, OT losses, points, goals for/against)
- **Player**: Represents an NHL player with attributes including player ID, name, position, team affiliation, biographical data, current season statistics, and career totals
- **Game**: Represents an NHL game with attributes including game ID, date/time, home team, away team, score, game state (scheduled, in progress, final)
- **Player Game Statistics**: Represents per-game statistics for a player including goals, assists, points, time on ice, plus/minus, related to a specific game
- **Team Game Statistics**: Represents per-game statistics for a team including goals for/against, shots, power play opportunities, related to a specific game
- **Player Last 10 Games Performance**: Represents recent performance metrics for a player over their last 10 games including total points earned and points percentage
- **Team Last 10 Games Performance**: Represents recent performance metrics for a team over their last 10 games including wins, losses, standings points earned, and points percentage
- **Schedule**: Represents the NHL game schedule with game times, teams involved, and game states used by data-job to determine when to fetch data

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Frontend users can load the homepage and see all statistics tables (all players, all teams, player last-10 percentages, team last-10 percentages) populated with complete current data within 2 seconds
- **SC-002**: Frontend users can search for any team or player and receive results within 300 milliseconds
- **SC-003**: Frontend users can navigate to any team or player detail page and see comprehensive statistics within 1 second
- **SC-004**: Data synchronization during game days updates statistics for all players in active games within 10 minutes of real-world events occurring
- **SC-005**: Duplicate detection prevents the same statistical event from being recorded more than once with 99.9% accuracy
- **SC-006**: Initial season data load completes for all 32 teams and all active players (approximately 800) within 15 minutes
- **SC-007**: All statistics calculations (points percentage for last 10 games, season totals) produce mathematically correct results as verified by automated tests
- **SC-008**: The data-job module runs continuously for 7 days without crashes or requiring manual intervention
- **SC-009**: API module handles 100 concurrent homepage requests without response time degradation beyond 3 seconds
- **SC-010**: System maintains data accuracy with less than 0.1% discrepancy compared to official NHL statistics after each sync cycle

### Assumptions

- The NHL API provides reliable game schedules at least 24 hours in advance
- Game statistics from the NHL API are updated in near-real-time (within 2-5 minutes of actual events)
- The shared database can be accessed by both API and data-job modules with appropriate connection pooling
- Frontend requirements for "minimal data" match the existing API contract for homepage endpoints
- Statistics calculations follow the same formulas currently implemented in the monolithic backend
- The data-job module will be deployed as a separate process/container with independent lifecycle from the API module
- Initial season data load will be triggered manually via administrative interface or CLI command, not automatically on startup
