package com.whoshot.nhl.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One goal of a {@link ScoreboardGame}, with its scorer and up to two assists.
 * Names are stored as the NHL API abbreviates them (e.g. "V. Arvidsson").
 */
@Entity
@Table(name = "scoreboard_goals")
@Getter
@Setter
@NoArgsConstructor
public class ScoreboardGoal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer goalNumber; // 1-based goal order within the game

    @Column
    private Integer period;

    @Column
    private String periodType;

    @Column
    private String timeInPeriod;

    @Column
    private String teamCode;

    @Column
    private Long scorerId;

    @Column
    private String scorerName;

    @Column
    private Long assist1Id;

    @Column
    private String assist1Name;

    @Column
    private Long assist2Id;

    @Column
    private String assist2Name;

    @Column
    private Integer awayScore; // score after this goal

    @Column
    private Integer homeScore;

    @Column
    private String strength; // ev, pp, sh
}
