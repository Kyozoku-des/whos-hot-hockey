package com.whoshot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for CurrentSeason entity operations.
 */
@Repository
public interface CurrentSeasonRepository extends JpaRepository<com.whoshot.entity.CurrentSeason, Long> {

    /**
     * Find the currently active season.
     */
    Optional<com.whoshot.entity.CurrentSeason> findByIsActiveTrue();

    /**
     * Find season by season ID.
     */
    Optional<com.whoshot.entity.CurrentSeason> findBySeasonId(String seasonId);
}
