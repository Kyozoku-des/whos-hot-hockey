package com.nhl.whoshotbackend.repository;

import com.nhl.whoshotbackend.entity.CurrentSeason;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for CurrentSeason entity operations.
 */
@Repository
public interface CurrentSeasonRepository extends JpaRepository<CurrentSeason, Long> {

    /**
     * Find the currently active season.
     */
    Optional<CurrentSeason> findByIsActiveTrue();

    /**
     * Find season by season ID.
     */
    Optional<CurrentSeason> findBySeasonId(String seasonId);
}
