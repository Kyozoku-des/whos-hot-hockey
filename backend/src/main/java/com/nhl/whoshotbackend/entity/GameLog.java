package com.nhl.whoshotbackend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity representing a player's performance in a specific game.
 * Used for calculating point streaks and hot/cold ratings.
 */
@Entity
@Table(name = "game_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GameLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long playerId;

    @Column(nullable = false)
    private Long gameId;

    @Column(nullable = false)
    private String gameDate;

    @Column
    private String opponentTeamCode;

    @Column
    private Boolean homeGame;

    @Column
    private Integer goals;

    @Column
    private Integer assists;

    @Column
    private Integer points;

    @Column
    private Integer plusMinus;

    @Column
    private Integer shots;

    @Column
    private Integer timeOnIce; // In seconds

    @Column
    private Boolean gameWon;
}
