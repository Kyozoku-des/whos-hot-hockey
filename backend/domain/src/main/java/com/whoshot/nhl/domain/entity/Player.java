package com.whoshot.nhl.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Formula;

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
            @AttributeOverride(name = "seasonId", column = @Column(name = "season_id", nullable = false, length = 8))
    })
    private PlayerId id;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Column private String fullName;
    @Column private String positionCode;
    @Column private String teamCode;
    // The team's logo for this season, read from teams instead of being stored per player.
    @Formula("(SELECT t.logo_url FROM teams t WHERE t.team_code = team_code AND t.season_id = season_id)")
    private String teamLogoUrl;
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

    /**
     * Composite primary key for a player row scoped by season.
     *
     * @param playerId NHL player identifier
     * @param seasonId season identifier in {@code YYYYYYYY} format
     */
    @Embeddable
    public record PlayerId(
            Long playerId,
            String seasonId
    ) implements Serializable {}
}
