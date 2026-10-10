-- Each team's official current roster: skaters and goalies, including players without points
-- or games. Rewritten per team by every full sync of the data job.

CREATE TABLE team_rosters (
    season_id VARCHAR(255) NOT NULL,
    player_id BIGINT NOT NULL,
    team_code VARCHAR(255) NOT NULL,
    last_updated TIMESTAMP(6),
    PRIMARY KEY (season_id, player_id)
);

CREATE INDEX idx_team_rosters_season_team ON team_rosters (season_id, team_code);
