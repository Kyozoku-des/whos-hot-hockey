package com.nhl.whoshotbackend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Entity representing an NHL player.
 * Uses composite key (playerId + season) to support multiple seasons.
 */
@Entity
@Table(name = "players")
@Data
@NoArgsConstructor
@AllArgsConstructor
@IdClass(Player.PlayerKey.class)
public class Player {

    @Id
    private Long playerId; // NHL API player ID

    @Id
    @Column(nullable = false)
    private String season; // Season ID (e.g., "20252026")

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Column
    private String fullName;

    @Column
    private String positionCode; // C, L, R, D, G

    @Column
    private String teamCode; // Three-letter team code

    @Column
    private Integer gamesPlayed;

    @Column
    private Integer goals;

    @Column
    private Integer assists;

    @Column
    private Integer points;

    @Column
    private Double pointsPerGame;

    @Column
    private Integer plusMinus;

    @Column
    private Integer penaltyMinutes;

    @Column
    private Integer powerPlayGoals;

    @Column
    private Integer shorthandedGoals;

    @Column
    private Integer gameWinningGoals;

    @Column
    private Integer overtimeGoals;

    @Column
    private Integer shots;

    @Column
    private Double shootingPercentage;

    @Column
    private Integer currentPointStreak; // Consecutive games with a point

    @Column
    private Double hotRating; // Points per game over last N games (calculated)

    @Column
    private Boolean hot; // true when on a hot streak

    @Column
    private Boolean cold; // true when on a cold streak

    @Column
    private Boolean pointStreak; // true when current streak qualifies

    @Column
    private String lastUpdated;

    /**
     * Composite key class for Player entity.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlayerKey implements Serializable {
        private Long playerId;
        private String season;
    }
}
