// Team games in a row a player has to miss before they show as absent
export const ABSENT_AFTER_GAMES = 2

export const isAbsent = (player) => (player?.gamesMissedInARow ?? 0) >= ABSENT_AFTER_GAMES

export const absentTitle = (player) =>
  `Missed the team's last ${player.gamesMissedInARow} games`

// The player's games and the team games they missed, oldest first. Missed
// games carry no stats, so the graph breaks and the table shows a dash there.
export const withMissedGames = (gameLogs, missedGames) =>
  [...gameLogs, ...missedGames.map(game => ({ ...game, missed: true }))]
    .sort((a, b) => a.gameDate.localeCompare(b.gameDate))
