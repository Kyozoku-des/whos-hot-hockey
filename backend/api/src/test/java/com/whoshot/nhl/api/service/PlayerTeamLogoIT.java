package com.whoshot.nhl.api.service;

import com.whoshot.nhl.domain.entity.Player;
import com.whoshot.nhl.domain.entity.Team;
import com.whoshot.nhl.domain.repository.PlayerRepository;
import com.whoshot.nhl.domain.repository.TeamRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A player's team logo is read from the team's row for the same season rather than stored per
 * player, so every season's players get one. Each test rolls back.
 */
@Tag("integration")
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PlayerService.class, SeasonResolver.class})
class PlayerTeamLogoIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

    @Autowired
    private PlayerService playerService;
    @Autowired
    private PlayerRepository playerRepository;
    @Autowired
    private TeamRepository teamRepository;
    @Autowired
    private TestEntityManager entityManager;

    @Test
    void usesTheLogoOfTheTeamInThePlayersSeason() {
        saveTeam("ARI", "20232024", "https://logo/ARI.svg");
        saveTeam("UTA", "20242025", "https://logo/UTA.svg");
        savePlayer(8478403L, "20232024", "ARI");
        savePlayer(8478403L, "20242025", "UTA");
        savePlayer(8478404L, "20242025", "SEA");
        entityManager.flush();
        entityManager.clear();

        assertThat(playerService.getPlayerDetail(8478403L, "20232024").teamLogoUrl())
                .isEqualTo("https://logo/ARI.svg");
        assertThat(playerService.getPlayerStandings("20242025"))
                .extracting(p -> p.playerId() + " " + p.teamLogoUrl())
                .containsExactlyInAnyOrder("8478403 https://logo/UTA.svg", "8478404 null");
    }

    private void saveTeam(String code, String season, String logoUrl) {
        Team team = new Team();
        team.setTeamCode(code);
        team.setSeasonId(season);
        team.setTeamName(code);
        team.setLogoUrl(logoUrl);
        teamRepository.save(team);
    }

    private void savePlayer(long id, String season, String teamCode) {
        playerRepository.save(Player.builder()
                .id(new Player.PlayerId(id, season))
                .firstName("Test").lastName("Player")
                .teamCode(teamCode)
                .build());
    }
}
