package com.whoshothockey.api.service;

import com.whoshothockey.api.dto.WatchlistDto;
import com.whoshothockey.api.dto.WatchlistDto.Direction;
import com.whoshothockey.api.dto.WatchlistDto.Rule;
import com.whoshothockey.api.dto.WatchlistDto.Type;
import com.whoshothockey.domain.entity.Player;
import com.whoshothockey.domain.entity.Team;
import com.whoshothockey.domain.repository.PlayerRepository;
import com.whoshothockey.domain.repository.TeamRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Service providing the homepage watchlist: players and teams whose form over their last ten games
 * differs most from what could be expected of them.
 *
 * <p>Recent form is compared with three baselines: the same player's or team's rate last season,
 * their rate in the games before the recent ones this season, and the league rate this season. Each
 * comparison is scored as the difference in standard deviations of a ten-game average, so scores of
 * different rules, and of players and teams, can be ranked together. A player's points per game is
 * treated as a Poisson rate and a team's point percentage as a proportion. Each player or team is
 * listed once, under the comparison that scores highest.
 */
@Service
public class WatchlistService {

    /** Games that make up recent form; the stored last-ten rates cover at most this many. */
    static final int RECENT_GAMES = 10;
    /** Games played this season before a player or team is considered at all. */
    static final int MIN_GAMES = 5;
    /** Games before the recent ones needed for an earlier-this-season baseline. */
    static final int MIN_EARLIER_GAMES = 10;
    /** Games last season needed for a last-season baseline. */
    static final int MIN_LAST_SEASON_GAMES = 20;
    /**
     * Lowest points-per-game rate used for a player's spread, so a player who rarely scores is not
     * listed after a couple of points.
     */
    static final double MIN_PLAYER_RATE = 0.15;
    /** Team point percentages are clamped to this distance from 0 and 1 for the spread. */
    static final double TEAM_RATE_MARGIN = 0.25;

    /** Highest score first; ties by name so the order is stable. */
    private static final Comparator<WatchlistDto.Item> BY_SCORE =
            Comparator.comparingDouble(WatchlistDto.Item::score).reversed()
                    .thenComparing(WatchlistDto.Item::name);

    private final PlayerRepository playerRepository;
    private final TeamRepository teamRepository;
    private final SeasonResolver seasonResolver;
    private final double minScore;
    private final int maxPlayers;
    private final int maxTeams;

    /**
     * @param minScore   score a comparison needs, in standard deviations, for the entry to be listed
     * @param maxPlayers most players listed
     * @param maxTeams   most teams listed
     */
    public WatchlistService(PlayerRepository playerRepository,
                            TeamRepository teamRepository,
                            SeasonResolver seasonResolver,
                            @Value("${watchlist.min-score:2.0}") double minScore,
                            @Value("${watchlist.max-players:12}") int maxPlayers,
                            @Value("${watchlist.max-teams:4}") int maxTeams) {
        this.playerRepository = playerRepository;
        this.teamRepository = teamRepository;
        this.seasonResolver = seasonResolver;
        this.minScore = minScore;
        this.maxPlayers = maxPlayers;
        this.maxTeams = maxTeams;
    }

    /**
     * @param season season identifier, or null to auto-detect active season
     * @return the season's standout players and teams, highest score first
     */
    public WatchlistDto getWatchlist(String season) {
        String resolved = seasonResolver.resolve(season);
        if (resolved == null) {
            return WatchlistDto.empty(null);
        }
        String previous = previousSeason(resolved);

        List<WatchlistDto.Item> players = playerItems(
                playerRepository.findByIdSeasonIdOrderByPointsDesc(resolved),
                previous == null ? List.of() : playerRepository.findByIdSeasonIdOrderByPointsDesc(previous));
        List<WatchlistDto.Item> teams = teamItems(
                teamRepository.findBySeasonIdOrderByPointsDesc(resolved),
                previous == null ? List.of() : teamRepository.findBySeasonIdOrderByPointsDesc(previous));

        return new WatchlistDto(resolved, Stream.concat(players.stream(), teams.stream())
                .sorted(BY_SCORE)
                .toList());
    }

    /**
     * The season before, e.g. "20242025" for "20252026".
     *
     * @return null when the identifier is malformed
     */
    static String previousSeason(String seasonId) {
        try {
            int startYear = Integer.parseInt(seasonId.substring(0, 4));
            return String.valueOf(startYear - 1) + startYear;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private List<WatchlistDto.Item> playerItems(List<Player> players, List<Player> lastSeason) {
        List<Player> eligible = players.stream().filter(WatchlistService::eligible).toList();
        Map<Long, Player> lastSeasonById = lastSeason.stream()
                .collect(Collectors.toMap(player -> player.getId().playerId(), Function.identity(), (a, b) -> a));
        Map<Boolean, Double> leagueRateByDefense = eligible.stream()
                .collect(Collectors.partitioningBy(WatchlistService::defenseman, Collectors.collectingAndThen(
                        Collectors.toList(), group -> rate(
                                group.stream().mapToInt(Player::getPoints).sum(),
                                group.stream().mapToInt(Player::getGamesPlayed).sum()))));

        return eligible.stream()
                .map(player -> best(playerSignals(player, lastSeasonById.get(player.getId().playerId()),
                        leagueRateByDefense.get(defenseman(player))))
                        .map(signal -> new WatchlistDto.Item(Type.PLAYER, String.valueOf(player.getId().playerId()),
                                player.getFullName(), player.getTeamCode(), signal.direction(), signal.rule(),
                                round(signal.recent(), 3), round(signal.baseline(), 3), signal.games(),
                                round(Math.abs(signal.score()), 2))))
                .flatMap(Optional::stream)
                .sorted(BY_SCORE)
                .limit(maxPlayers)
                .toList();
    }

    /** Skaters with enough games and stored recent form; goalies are left out. */
    private static boolean eligible(Player player) {
        return player.getGamesPlayed() != null && player.getGamesPlayed() >= MIN_GAMES
                && player.getPoints() != null && player.getPointsPerLastNGames() != null
                && !"G".equals(player.getPositionCode());
    }

    private static boolean defenseman(Player player) {
        return "D".equals(player.getPositionCode());
    }

    private static List<Signal> playerSignals(Player player, Player lastSeason, double leagueRate) {
        int games = Math.min(player.getGamesPlayed(), RECENT_GAMES);
        double recent = player.getPointsPerLastNGames();
        List<Signal> signals = new ArrayList<>(3);

        if (lastSeason != null && lastSeason.getGamesPlayed() != null && lastSeason.getPoints() != null
                && lastSeason.getGamesPlayed() >= MIN_LAST_SEASON_GAMES) {
            double baseline = rate(lastSeason.getPoints(), lastSeason.getGamesPlayed());
            signals.add(playerSignal(Rule.LAST_SEASON, recent, baseline, games));
        }

        int earlierGames = player.getGamesPlayed() - games;
        if (earlierGames >= MIN_EARLIER_GAMES) {
            long recentPoints = Math.round(recent * games);
            double baseline = rate(player.getPoints() - recentPoints, earlierGames);
            signals.add(playerSignal(Rule.EARLIER_THIS_SEASON, recent, baseline, games));
        }

        // Only upward: almost every depth player is below the league rate, which says nothing
        // about their form.
        Signal league = playerSignal(Rule.LEAGUE, recent, leagueRate, games);
        if (league.direction() == Direction.UP) {
            signals.add(league);
        }
        return signals;
    }

    private static Signal playerSignal(Rule rule, double recent, double baseline, int games) {
        double spread = Math.sqrt(Math.max(baseline, MIN_PLAYER_RATE) / games);
        return new Signal(rule, recent, baseline, games, (recent - baseline) / spread);
    }

    private List<WatchlistDto.Item> teamItems(List<Team> teams, List<Team> lastSeason) {
        List<Team> eligible = teams.stream().filter(WatchlistService::eligible).toList();
        Map<String, Team> lastSeasonByCode = lastSeason.stream()
                .collect(Collectors.toMap(Team::getTeamCode, Function.identity(), (a, b) -> a));
        double leagueRate = rate(eligible.stream().mapToInt(Team::getPoints).sum(),
                2 * eligible.stream().mapToInt(Team::getGamesPlayed).sum());

        return eligible.stream()
                .map(team -> best(teamSignals(team, lastSeasonByCode.get(team.getTeamCode()), leagueRate))
                        .map(signal -> new WatchlistDto.Item(Type.TEAM, team.getTeamCode(), team.getTeamName(),
                                team.getTeamCode(), signal.direction(), signal.rule(),
                                round(signal.recent(), 3), round(signal.baseline(), 3), signal.games(),
                                round(Math.abs(signal.score()), 2))))
                .flatMap(Optional::stream)
                .sorted(BY_SCORE)
                .limit(maxTeams)
                .toList();
    }

    private static boolean eligible(Team team) {
        return team.getGamesPlayed() != null && team.getGamesPlayed() >= MIN_GAMES
                && team.getPoints() != null && team.getLast10GamesPointPercentage() != null
                && team.getLast10GamesPPG() != null;
    }

    private static List<Signal> teamSignals(Team team, Team lastSeason, double leagueRate) {
        int games = Math.min(team.getGamesPlayed(), RECENT_GAMES);
        double recent = team.getLast10GamesPointPercentage();
        List<Signal> signals = new ArrayList<>(3);

        if (lastSeason != null && lastSeason.getGamesPlayed() != null && lastSeason.getPoints() != null
                && lastSeason.getGamesPlayed() >= MIN_LAST_SEASON_GAMES) {
            double baseline = rate(lastSeason.getPoints(), 2 * lastSeason.getGamesPlayed());
            signals.add(teamSignal(Rule.LAST_SEASON, recent, baseline, games));
        }

        int earlierGames = team.getGamesPlayed() - games;
        if (earlierGames >= MIN_EARLIER_GAMES) {
            long recentPoints = Math.round(team.getLast10GamesPPG() * games);
            double baseline = rate(team.getPoints() - recentPoints, 2 * earlierGames);
            signals.add(teamSignal(Rule.EARLIER_THIS_SEASON, recent, baseline, games));
        }

        // Both ways: every team is a regular, so a slump against the league says something.
        signals.add(teamSignal(Rule.LEAGUE, recent, leagueRate, games));
        return signals;
    }

    private static Signal teamSignal(Rule rule, double recent, double baseline, int games) {
        double clamped = Math.clamp(baseline, TEAM_RATE_MARGIN, 1 - TEAM_RATE_MARGIN);
        double spread = Math.sqrt(clamped * (1 - clamped) / games);
        return new Signal(rule, recent, baseline, games, (recent - baseline) / spread);
    }

    /** The comparison that stands out most, if any reaches the minimum score; ties keep rule order. */
    private Optional<Signal> best(List<Signal> signals) {
        return signals.stream()
                .filter(signal -> Math.abs(signal.score()) >= minScore)
                .reduce((a, b) -> Math.abs(b.score()) > Math.abs(a.score()) ? b : a);
    }

    private static double rate(long amount, long count) {
        return count > 0 ? (double) amount / count : 0.0;
    }

    private static double round(double value, int decimals) {
        double scale = Math.pow(10, decimals);
        return Math.round(value * scale) / scale;
    }

    /**
     * One comparison of recent form with a baseline.
     *
     * @param score signed difference in standard deviations; positive when recent form is better
     */
    private record Signal(Rule rule, double recent, double baseline, int games, double score) {
        Direction direction() {
            return score >= 0 ? Direction.UP : Direction.DOWN;
        }
    }
}
