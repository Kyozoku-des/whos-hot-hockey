package com.whoshot.nhl.datajob.util;

import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * Utility class for validating and working with NHL seasons.
 */
@Service
public class SeasonValidator {

    // NHL founded in 1917, use 1917-1918 as minimum season
    private static final int MIN_SEASON_START_YEAR = 1917;

    /**
     * Get the current NHL season ID.
     * NHL seasons span two years (e.g., 2025-2026 season ID is 20252026).
     * Season starts in October and ends in June.
     *
     * @return Current season ID in format YYYYYYYY
     */
    public static String getCurrentSeasonId() {
        LocalDate now = LocalDate.now();
        int year = now.getYear();
        int month = now.getMonthValue();

        // Jan-June still belongs to the season that started the previous year; from July on,
        // the off-season already counts toward the season starting this year.
        int startYear = month <= 6 ? year - 1 : year;

        int endYear = startYear + 1;
        return String.format("%d%d", startYear, endYear);
    }

    /**
     * Validate if a season ID is in valid format and within reasonable bounds.
     *
     * @param seasonId Season ID to validate (e.g., "20252026")
     * @return true if valid, false otherwise
     */
    public static boolean isNotValidSeasonId(String seasonId) {
        if (seasonId == null || seasonId.length() != 8) {
            return true;
        }

        try {
            int startYear = Integer.parseInt(seasonId.substring(0, 4));
            int endYear = Integer.parseInt(seasonId.substring(4, 8));

            // End year must be exactly 1 year after start year
            if (endYear != startYear + 1) {
                return true;
            }

            // Check minimum bound
            if (startYear < MIN_SEASON_START_YEAR) {
                return true;
            }

            // Check maximum bound (current season + 1 for future planning)
            String currentSeasonId = getCurrentSeasonId();
            int currentStartYear = Integer.parseInt(currentSeasonId.substring(0, 4));

            return startYear > currentStartYear + 1;
        } catch (NumberFormatException e) {
            return true;
        }
    }
}
