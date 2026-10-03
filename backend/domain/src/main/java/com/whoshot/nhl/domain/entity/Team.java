package com.whoshot.nhl.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Entity representing an NHL team.
 * Uses composite key (teamCode + season) to support multiple seasons.
 */
@Entity
@Table(name = "teams")
@Data
@NoArgsConstructor
@AllArgsConstructor
@IdClass(Team.TeamKey.class)
public class Team {

    @Id
    private String teamCode; // Three-letter team code (e.g., TOR, BOS)

    @Id
    @Column(nullable = false)
    private String season; // Season ID (e.g., "20252026")

    @Column(nullable = false)
    private String teamName; // Full team name

    @Column
    private String franchiseName;

    @Column
    private String logoUrl; // URL to team logo image

    @Column
    private Integer gamesPlayed;

    @Column
    private Integer wins;

    @Column
    private Integer losses;

    @Column
    private Integer overtimeLosses;

    @Column
    private Integer points;

    @Column
    private Double pointPercentage;

    @Column
    private Integer goalsFor;

    @Column
    private Integer goalsAgainst;

    @Column
    private Integer goalDifferential;

    @Column
    private String conferenceName;

    @Column
    private String divisionName;

    @Column
    private Integer currentWinStreak;

    @Column
    private Integer currentLossStreak;

    // Explicit names: Boot 4's snake-case strategy inserts "_" after digits (last10_games...),
    // which no longer matches the existing V1 column names.
    @Column(name = "last10games_win_percentage")
    private Double last10GamesWinPercentage; // Win percentage over last 10 games

    @Column(name = "last10games_point_percentage")
    private Double last10GamesPointPercentage; // Point percentage over last 10 games (points / (games * 2))

    @Column(name = "last10gamesppg")
    private Double last10GamesPPG; // Points per game over last 10 games

    @Column
    private Boolean hot; // true when recent stretch exceeds hot threshold

    @Column
    private Boolean cold; // true when recent stretch is below cold threshold

    @Column
    private Boolean pointStreak; // true when team has consecutive point games

    @Column
    private String nextOpponentCode; // Next opponent team code

    @Column
    private String nextGameDate; // Next game date (ISO format)

    @Column
    private Boolean nextGameIsHome; // true if next game is home

    @Column
    private String lastUpdated;

    /**
     * Composite key class for Team entity.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TeamKey implements Serializable {
        private String teamCode;
        private String season;
    }
}
