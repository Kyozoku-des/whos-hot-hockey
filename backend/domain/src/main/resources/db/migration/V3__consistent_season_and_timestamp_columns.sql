-- Name the season column the same in every table (game_logs, team_games and current_season
-- already use season_id), and store every last_updated as a timestamp like players does.

ALTER TABLE players RENAME COLUMN season TO season_id;
ALTER TABLE teams RENAME COLUMN season TO season_id;

-- Existing values were written with LocalDateTime.toString() (ISO-8601), which casts directly.
ALTER TABLE teams
    ALTER COLUMN last_updated TYPE TIMESTAMP(6) USING NULLIF(last_updated, '')::timestamp;
ALTER TABLE current_season
    ALTER COLUMN last_updated TYPE TIMESTAMP(6) USING NULLIF(last_updated, '')::timestamp;
