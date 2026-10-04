package com.whoshot.nhl.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Entity representing a hockey player and their statistics for a given season.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "players")
public class Player {

    @EmbeddedId
    @AttributeOverrides({
            @AttributeOverride(name = "playerId", column = @Column(name = "player_id", nullable = false)),
            @AttributeOverride(name = "season",   column = @Column(name = "season",    nullable = false, length = 8))
    })
    private PlayerId id;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Column private String fullName;
    @Column private String positionCode;
    @Column private String teamCode;
    @Column private String teamLogoUrl;
    @Column private Integer gamesPlayed;
    @Column private Integer goals;
    @Column private Integer assists;
    @Column private Integer points;
    @Column private Double pointsPerGame;
    @Column private Integer plusMinus;
    @Column private Integer currentPointStreak;
    @Column private Integer currentPointlessStreak;
    @Column private Double pointsPerLastNGames;
    @Column private LocalDateTime lastUpdated;
    @Column private String headshotUrl;

    /**
     * Composite primary key for a player row scoped by season.
     *
     * @param playerId NHL player identifier
     * @param season season identifier in {@code YYYYYYYY} format
     */
    @Embeddable
    public record PlayerId(
            Long playerId,
            String season
    ) implements Serializable {}
}
