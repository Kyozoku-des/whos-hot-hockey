package com.whoshothockey.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * When the data job last finished a kind of sync without a run-level failure.
 */
@Entity
@Table(name = "sync_status")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SyncStatus {

    /** The sync of current-season stats: full syncs and game-time polls. */
    public static final String STATS = "stats";

    @Id
    private String syncName;

    @Column(nullable = false)
    private Instant lastSuccessAt;
}
