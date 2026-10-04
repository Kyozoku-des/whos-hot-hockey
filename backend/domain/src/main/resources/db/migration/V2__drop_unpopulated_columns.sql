-- No ingestion path ever wrote these columns, so every row holds NULL in them.
-- game_logs.shots is populated and stays; only the season-level players.shots goes.

ALTER TABLE players
    DROP COLUMN penalty_minutes,
    DROP COLUMN power_play_goals,
    DROP COLUMN shorthanded_goals,
    DROP COLUMN game_winning_goals,
    DROP COLUMN overtime_goals,
    DROP COLUMN shots,
    DROP COLUMN shooting_percentage,
    DROP COLUMN hot,
    DROP COLUMN cold,
    DROP COLUMN date,
    DROP COLUMN opponent_abbrev,
    DROP COLUMN home_road_flag;

ALTER TABLE teams
    DROP COLUMN franchise_name,
    DROP COLUMN last10games_win_percentage,
    DROP COLUMN hot,
    DROP COLUMN cold,
    DROP COLUMN point_streak,
    DROP COLUMN next_opponent_code,
    DROP COLUMN next_game_date,
    DROP COLUMN next_game_is_home;
