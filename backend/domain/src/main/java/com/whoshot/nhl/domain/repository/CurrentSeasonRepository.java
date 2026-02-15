package com.whoshot.nhl.domain.repository;

import com.whoshot.nhl.domain.entity.CurrentSeason;
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
     *
     * @return active season record if present
     */
    Optional<CurrentSeason> findByIsActiveTrue();

    /**
     * Find season by season ID.
     *
     * @param seasonId season identifier
     * @return season record matching the provided identifier, if present
     */
    Optional<CurrentSeason> findBySeasonId(String seasonId);
}
