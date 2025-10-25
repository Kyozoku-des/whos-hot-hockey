package com.nhl.whoshotbackend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity representing the current active NHL season.
 * There should only be one active season at a time.
 */
@Entity
@Table(name = "current_season")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CurrentSeason {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String seasonId; // Format: "20242025"

    @Column
    private String seasonDisplayName; // Format: "2024-2025"

    @Column
    private Boolean isActive;

    @Column
    private String lastUpdated;
}
