package com.whoshot.nhl.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * A team's next unfinished game in a season, used to show its next opponent.
 * One row per team and season; replaced whenever the team's schedule is written.
 */
@Entity
@Table(name = "team_next_games")
@Data
@NoArgsConstructor
@AllArgsConstructor
@IdClass(TeamNextGame.TeamNextGameKey.class)
public class TeamNextGame {

    @Id
    private String teamCode;

    @Id
    private String seasonId;

    @Column(nullable = false)
    private Long gameId;

    @Column(nullable = false)
    private String gameDate; // Local game date (yyyy-MM-dd)

    @Column
    private String startTimeUtc;

    @Column
    private String gameState; // NHL game state, e.g. FUT, LIVE

    @Column
    private String opponentTeamCode;

    @Column
    private Boolean homeGame;

    @Column
    private LocalDateTime lastUpdated;

    /**
     * Composite key class for TeamNextGame entity.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TeamNextGameKey implements Serializable {
        private String teamCode;
        private String seasonId;
    }
}
