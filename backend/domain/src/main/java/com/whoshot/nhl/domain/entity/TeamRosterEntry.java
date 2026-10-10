package com.whoshot.nhl.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * A player on a team's official roster in a season.
 * One row per player and season; a team's rows are replaced whenever its roster is written.
 */
@Entity
@Table(name = "team_rosters")
@Data
@NoArgsConstructor
@AllArgsConstructor
@IdClass(TeamRosterEntry.TeamRosterKey.class)
public class TeamRosterEntry {

    @Id
    private String seasonId;

    @Id
    private Long playerId;

    @Column(nullable = false)
    private String teamCode;

    @Column
    private LocalDateTime lastUpdated;

    /**
     * Composite key class for TeamRosterEntry entity.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TeamRosterKey implements Serializable {
        private String seasonId;
        private Long playerId;
    }
}
