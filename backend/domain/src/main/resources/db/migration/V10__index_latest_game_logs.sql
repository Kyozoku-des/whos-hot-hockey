-- Each player's latest game in a season is looked up for every row of the player lists, to tell
-- how many of their team's games they have missed since.

CREATE INDEX idx_game_logs_player_season_date ON game_logs (player_id, season_id, game_date DESC);
