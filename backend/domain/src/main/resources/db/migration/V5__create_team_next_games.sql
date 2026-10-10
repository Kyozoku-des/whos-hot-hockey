-- Each team's next unfinished game, shown as the next opponent on player and team rows (issue #44).
-- Rewritten by the data job whenever it writes the team's schedule; removed once none is left.

CREATE TABLE team_next_games (
    team_code VARCHAR(255) NOT NULL,
    season_id VARCHAR(255) NOT NULL,
    game_id BIGINT NOT NULL,
    game_date VARCHAR(255) NOT NULL,
    start_time_utc VARCHAR(255),
    game_state VARCHAR(255),
    opponent_team_code VARCHAR(255),
    home_game BOOLEAN,
    last_updated TIMESTAMP(6),
    PRIMARY KEY (team_code, season_id)
);
