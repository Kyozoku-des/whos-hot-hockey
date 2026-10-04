package com.whoshot.nhl.api.service;

import com.whoshot.nhl.api.dto.SearchIndexDto;
import com.whoshot.nhl.api.dto.SearchResultDto;
import com.whoshot.nhl.domain.entity.Player;
import com.whoshot.nhl.domain.entity.Team;
import com.whoshot.nhl.domain.repository.PlayerRepository;
import com.whoshot.nhl.domain.repository.TeamRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The search projections' JPQL constructor expressions build {@code SearchResult} rows from the
 * real Flyway schema. Each test rolls back.
 */
@Tag("integration")
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({SearchService.class, SeasonResolver.class})
class SearchIndexIT {

    private static final String SEASON = "20252026";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

    @Autowired
    private SearchService searchService;
    @Autowired
    private PlayerRepository playerRepository;
    @Autowired
    private TeamRepository teamRepository;

    @Test
    void buildsTeamAndPlayerEntriesForTheSeason() {
        Team team = new Team();
        team.setTeamCode("EDM");
        team.setSeasonId(SEASON);
        team.setTeamName("Edmonton Oilers");
        team.setLogoUrl("https://logo.svg");
        teamRepository.save(team);
        playerRepository.save(Player.builder()
                .id(new Player.PlayerId(8478402L, SEASON))
                .firstName("Connor").lastName("McDavid")
                .positionCode("C").teamCode("EDM").headshotUrl("https://headshot.png")
                .build());

        SearchIndexDto index = searchService.getSearchIndex(SEASON);

        assertThat(index.results()).containsExactly(
                new SearchResultDto("TEAM", "EDM", "Edmonton Oilers", "EDM", "EDM", "https://logo.svg"),
                new SearchResultDto("PLAYER", "8478402", "Connor McDavid", "C", "EDM", "https://headshot.png"));
    }
}
