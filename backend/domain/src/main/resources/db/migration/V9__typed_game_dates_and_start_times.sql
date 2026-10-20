-- Game dates and start times were stored as text: local dates as yyyy-MM-dd and UTC start times as
-- ISO-8601 with a trailing Z, both of which cast directly.

ALTER TABLE game_logs
    ALTER COLUMN game_date TYPE DATE USING game_date::date;

ALTER TABLE team_games
    ALTER COLUMN game_date TYPE DATE USING game_date::date;

ALTER TABLE scoreboard_games
    ALTER COLUMN game_date TYPE DATE USING game_date::date,
    ALTER COLUMN start_time_utc TYPE TIMESTAMP(6) WITH TIME ZONE USING start_time_utc::timestamptz;

ALTER TABLE team_next_games
    ALTER COLUMN game_date TYPE DATE USING game_date::date,
    ALTER COLUMN start_time_utc TYPE TIMESTAMP(6) WITH TIME ZONE USING start_time_utc::timestamptz;
