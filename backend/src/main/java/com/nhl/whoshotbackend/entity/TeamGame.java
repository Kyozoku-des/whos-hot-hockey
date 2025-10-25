package com.nhl.whoshotbackend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity representing a team's game result.
 * Used for calculating win/loss streaks.
 */
@Entity
@Table(name = "team_games")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TeamGame {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long gameId;

    @Column(nullable = false)
    private String teamCode;

    @Column(nullable = false)
    private String gameDate;

    @Column
    private String opponentTeamCode;

    @Column
    private Boolean homeGame;

    @Column
    private Integer goalsFor;

    @Column
    private Integer goalsAgainst;

    @Column
    private Boolean won;

    @Column
    private Boolean overtimeLoss;

    @Column
    private String gameType; // Regular season or playoffs

    @Column
    private String seasonId; // NHL season identifier (e.g., "20252026")

    @Column
    private Integer gameNumber; // Game number in the season (1-82)
}
