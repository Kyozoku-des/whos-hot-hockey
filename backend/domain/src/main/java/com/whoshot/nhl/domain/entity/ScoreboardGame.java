package com.whoshot.nhl.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A game shown in the score ticker, with its scoring summary in goal order.
 * Keyed by the NHL game id; replaced on every sync of its game day.
 */
@Entity
@Table(name = "scoreboard_games")
@Getter
@Setter
@NoArgsConstructor
public class ScoreboardGame {

    @Id
    private Long gameId;

    @Column(nullable = false)
    private String gameDate;

    @Column
    private String seasonId;

    @Column
    private String startTimeUtc;

    @Column
    private String gameState; // NHL game state, e.g. LIVE, OFF

    @Column
    private String awayTeamCode;

    @Column
    private Integer awayScore;

    @Column
    private String homeTeamCode;

    @Column
    private Integer homeScore;

    @Column
    private String lastPeriodType; // REG, OT or SO once the game has ended

    @Column
    private LocalDateTime lastUpdated;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "game_id", nullable = false)
    @OrderBy("goalNumber ASC")
    private List<ScoreboardGoal> goals = new ArrayList<>();
}
