package com.whoshot.nhl.domain.repository;

import com.whoshot.nhl.domain.entity.SyncStatus;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for SyncStatus entity operations.
 */
public interface SyncStatusRepository extends JpaRepository<SyncStatus, String> {
}
