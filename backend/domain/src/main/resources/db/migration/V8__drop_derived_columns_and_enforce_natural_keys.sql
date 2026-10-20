-- Columns the application now derives instead of storing:
--   players.headshot_url        no longer stored or served
--   players.team_logo_url       read from teams.logo_url for the same season
--   players.full_name           first_name || ' ' || last_name
--   teams.goal_differential     goals_for - goals_against

ALTER TABLE players
    DROP COLUMN headshot_url,
    DROP COLUMN team_logo_url,
    DROP COLUMN full_name;

ALTER TABLE teams
    DROP COLUMN goal_differential;

-- The data job upserts game rows on their natural keys, which the entities declare as unique but
-- V1 never enforced. Keep the most recently inserted row of any duplicate before enforcing them.
DELETE FROM game_logs g
USING game_logs newer
WHERE newer.player_id = g.player_id AND newer.game_id = g.game_id AND newer.id > g.id;

DELETE FROM team_games g
USING team_games newer
WHERE newer.team_code = g.team_code AND newer.game_id = g.game_id AND newer.id > g.id;

-- Also the index for per-player and per-team lookups, which previously scanned the whole table.
CREATE UNIQUE INDEX uq_game_logs_player_game ON game_logs (player_id, game_id);
CREATE UNIQUE INDEX uq_team_games_team_game ON team_games (team_code, game_id);
