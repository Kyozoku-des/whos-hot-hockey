package com.whoshothockey.api.service;

import com.whoshothockey.api.dto.WatchlistDto;
import com.whoshothockey.api.dto.WatchlistDto.Direction;
import com.whoshothockey.api.dto.WatchlistDto.Rule;
import com.whoshothockey.api.dto.WatchlistDto.Type;
import com.whoshothockey.domain.entity.Player;
import com.whoshothockey.domain.entity.Team;
import com.whoshothockey.domain.repository.CurrentSeasonRepository;
import com.whoshothockey.domain.repository.PlayerRepository;
import com.whoshothockey.domain.repository.TeamRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WatchlistServiceTest {

    private static final String SEASON = "20262027";
    private static final String LAST_SEASON = "20252026";

    private final PlayerRepository playerRepository = mock(PlayerRepository.class);
    private final TeamRepository teamRepository = mock(TeamRepository.class);
    private final CurrentSeasonRepository currentSeasonRepository = mock(CurrentSeasonRepository.class);

    private final List<Player> players = new ArrayList<>(averageForwards());
    private final List<Player> lastSeasonPlayers = new ArrayList<>();
    private final List<Team> teams = new ArrayList<>(averageTeams());
    private final List<Team> lastSeasonTeams = new ArrayList<>();

    private WatchlistDto watchlist() {
        return watchlist(12, 4);
    }

    private WatchlistDto watchlist(int maxPlayers, int maxTeams) {
        when(playerRepository.findByIdSeasonIdOrderByPointsDesc(SEASON)).thenReturn(players);
        when(playerRepository.findByIdSeasonIdOrderByPointsDesc(LAST_SEASON)).thenReturn(lastSeasonPlayers);
        when(teamRepository.findBySeasonIdOrderByPointsDesc(SEASON)).thenReturn(teams);
        when(teamRepository.findBySeasonIdOrderByPointsDesc(LAST_SEASON)).thenReturn(lastSeasonTeams);
        return new WatchlistService(playerRepository, teamRepository, new SeasonResolver(currentSeasonRepository),
                2.0, maxPlayers, maxTeams).getWatchlist(SEASON);
    }

    @Test
    void averageForm_listsNobody() {
        assertThat(watchlist().items()).isEmpty();
    }

    @Test
    void breakoutAgainstLastSeason_isListedOnceUnderTheHighestScoringRule() {
        // 0.40 last season, 1.25 over eight games now; also well above the league rate
        players.add(player(100L, SEASON, "C", 8, 10, 1.25));
        lastSeasonPlayers.add(player(100L, LAST_SEASON, "C", 40, 16, 0.4));

        assertThat(watchlist().items()).singleElement().satisfies(item -> {
            assertThat(item.type()).isEqualTo(Type.PLAYER);
            assertThat(item.id()).isEqualTo("100");
            assertThat(item.teamCode()).isEqualTo("EDM");
            assertThat(item.direction()).isEqualTo(Direction.UP);
            assertThat(item.rule()).isEqualTo(Rule.LAST_SEASON);
            assertThat(item.recent()).isEqualTo(1.25);
            assertThat(item.baseline()).isEqualTo(0.4);
            assertThat(item.recentGames()).isEqualTo(8);
            // (1.25 - 0.40) / sqrt(0.40 / 8)
            assertThat(item.score()).isEqualTo(3.8);
        });
    }

    @Test
    void lastSeasonWithFewGames_isNoBaseline() {
        players.add(player(100L, SEASON, "C", 8, 6, 0.75));
        lastSeasonPlayers.add(player(100L, LAST_SEASON, "C", 19, 2, 0.105));

        assertThat(watchlist().items()).isEmpty();
    }

    @Test
    void slumpAgainstEarlierThisSeason_isListedAsDown() {
        // 29 points in the first 20 games, 1 in the last ten
        players.add(player(100L, SEASON, "C", 30, 30, 0.1));

        assertThat(watchlist().items()).singleElement().satisfies(item -> {
            assertThat(item.direction()).isEqualTo(Direction.DOWN);
            assertThat(item.rule()).isEqualTo(Rule.EARLIER_THIS_SEASON);
            assertThat(item.baseline()).isEqualTo(1.45);
            assertThat(item.score()).isEqualTo(3.55);
        });
    }

    @Test
    void earlierThisSeason_needsTenEarlierGames() {
        players.add(player(100L, SEASON, "C", 19, 19, 0.1));

        assertThat(watchlist().items()).isEmpty();
    }

    @Test
    void farAboveTheLeague_isListedWithoutHistory() {
        players.add(player(100L, SEASON, "C", 8, 16, 2.0));

        assertThat(watchlist().items()).singleElement().satisfies(item -> {
            assertThat(item.rule()).isEqualTo(Rule.LEAGUE);
            assertThat(item.direction()).isEqualTo(Direction.UP);
        });
    }

    @Test
    void belowTheLeague_isNeverListed() {
        players.add(player(100L, SEASON, "C", 10, 0, 0.0));

        assertThat(watchlist().items()).isEmpty();
    }

    @Test
    void defensemen_areComparedWithDefensemen() {
        // 0.9 is ordinary for these forwards but far above the other defensemen
        players.addAll(LongStream.rangeClosed(200, 209)
                .mapToObj(id -> player(id, SEASON, "D", 10, 3, 0.3)).toList());
        players.add(player(100L, SEASON, "D", 10, 9, 0.9));
        players.add(player(101L, SEASON, "C", 10, 9, 0.9));

        assertThat(watchlist().items()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo("100");
            assertThat(item.rule()).isEqualTo(Rule.LEAGUE);
        });
    }

    @Test
    void goaliesAndPlayersWithFewGames_areLeftOut() {
        players.add(player(100L, SEASON, "G", 8, 3, 0.375));
        players.add(player(101L, SEASON, "C", 4, 8, 2.0));

        assertThat(watchlist().items()).isEmpty();
    }

    @Test
    void teamTurnaroundAgainstLastSeason_isListed() {
        // .400 last season, 14 of 16 points over eight games now
        teams.add(team("ANA", SEASON, 8, 14, 0.875));
        lastSeasonTeams.add(team("ANA", LAST_SEASON, 82, 66, 0.5));

        assertThat(watchlist().items()).singleElement().satisfies(item -> {
            assertThat(item.type()).isEqualTo(Type.TEAM);
            assertThat(item.id()).isEqualTo("ANA");
            assertThat(item.name()).isEqualTo("Team ANA");
            assertThat(item.rule()).isEqualTo(Rule.LAST_SEASON);
            assertThat(item.baseline()).isEqualTo(0.402);
            assertThat(item.direction()).isEqualTo(Direction.UP);
        });
    }

    @Test
    void teamSlumpAgainstTheLeague_isListedAsDown() {
        teams.add(team("SJS", SEASON, 10, 2, 0.1));

        assertThat(watchlist().items()).singleElement().satisfies(item -> {
            assertThat(item.rule()).isEqualTo(Rule.LEAGUE);
            assertThat(item.direction()).isEqualTo(Direction.DOWN);
        });
    }

    @Test
    void itemsAreRankedByScoreAcrossPlayersAndTeams_withinEachLimit() {
        players.add(player(100L, SEASON, "C", 8, 16, 2.0));
        players.add(player(101L, SEASON, "C", 8, 20, 2.5));
        players.add(player(102L, SEASON, "C", 8, 24, 3.0));
        teams.add(team("SJS", SEASON, 10, 0, 0.0));

        assertThat(watchlist(2, 4).items()).extracting(WatchlistDto.Item::id)
                .containsExactly("102", "101", "SJS");
    }

    @Test
    void withoutActiveSeason_returnsEmptyWatchlist() {
        when(currentSeasonRepository.findByIsActiveTrue()).thenReturn(Optional.empty());
        var service = new WatchlistService(playerRepository, teamRepository,
                new SeasonResolver(currentSeasonRepository), 2.0, 12, 4);

        assertThat(service.getWatchlist(null)).isEqualTo(WatchlistDto.empty(null));
    }

    @Test
    void previousSeason_isTheSeasonBefore() {
        assertThat(WatchlistService.previousSeason("20262027")).isEqualTo("20252026");
        assertThat(WatchlistService.previousSeason("bad")).isNull();
    }

    /** Twenty forwards at a steady 0.5 points per game, which sets the league rate. */
    private static List<Player> averageForwards() {
        return LongStream.rangeClosed(1, 20).mapToObj(id -> player(id, SEASON, "C", 10, 5, 0.5)).toList();
    }

    /** Twenty teams at a steady .500 point percentage. */
    private static List<Team> averageTeams() {
        return LongStream.rangeClosed(1, 20).mapToObj(id -> team("T" + id, SEASON, 10, 10, 0.5)).toList();
    }

    private static Player player(long playerId, String season, String position, int gamesPlayed, int points,
                                 double pointsPerLastTen) {
        return Player.builder()
                .id(new Player.PlayerId(playerId, season))
                .firstName("First")
                .lastName("Last " + playerId)
                .positionCode(position)
                .teamCode("EDM")
                .gamesPlayed(gamesPlayed)
                .points(points)
                .pointsPerGame((double) points / gamesPlayed)
                .pointsPerLastNGames(pointsPerLastTen)
                .build();
    }

    private static Team team(String teamCode, String season, int gamesPlayed, int points,
                             double lastTenPointPercentage) {
        Team team = new Team();
        team.setTeamCode(teamCode);
        team.setSeasonId(season);
        team.setTeamName("Team " + teamCode);
        team.setGamesPlayed(gamesPlayed);
        team.setPoints(points);
        team.setLast10GamesPointPercentage(lastTenPointPercentage);
        team.setLast10GamesPPG(lastTenPointPercentage * 2);
        return team;
    }
}
