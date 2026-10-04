package com.whoshot.nhl.api.service;

import com.whoshot.nhl.domain.entity.CurrentSeason;
import com.whoshot.nhl.domain.repository.CurrentSeasonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Resolves the optional {@code season} request parameter shared by every endpoint.
 */
@Component
@RequiredArgsConstructor
public class SeasonResolver {

    private final CurrentSeasonRepository currentSeasonRepository;

    /**
     * Returns the requested season, or the active season when none was given.
     *
     * @param season season identifier, or null/blank to auto-detect the active season
     * @return the season to query, or null when none was given and no season is active
     */
    public String resolve(String season) {
        if (season != null && !season.isBlank()) {
            return season;
        }
        return currentSeasonRepository.findByIsActiveTrue()
                .map(CurrentSeason::getSeasonId)
                .orElse(null);
    }
}
