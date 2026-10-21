package com.whoshothockey.domain.repository;

import com.whoshothockey.domain.entity.SyncStatus;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for SyncStatus entity operations.
 */
public interface SyncStatusRepository extends JpaRepository<SyncStatus, String> {
}
