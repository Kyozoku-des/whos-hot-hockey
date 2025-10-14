package com.nhl.whoshotbackend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity representing an NHL team.
 */
@Entity
@Table(name = "teams")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String teamCode; // Three-letter team code (e.g., TOR, BOS)

    @Column(nullable = false)
    private String teamName; // Full team name

    @Column
    private String franchiseName;

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

    @Column
    private String lastUpdated;
}
