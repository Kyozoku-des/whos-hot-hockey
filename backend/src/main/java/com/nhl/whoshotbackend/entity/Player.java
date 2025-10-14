package com.nhl.whoshotbackend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity representing an NHL player.
 */
@Entity
@Table(name = "players")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Player {

    @Id
    private Long playerId; // NHL API player ID

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
    private String lastUpdated;
}
