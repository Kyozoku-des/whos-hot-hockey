package com.nhl.whoshotbackend.util;

import java.time.LocalDate;

/**
 * Utility class for validating and working with NHL seasons.
 */
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

        // If we're in January-June, we're in the second half of the season
        // If we're in July-December, we're in the first half or pre-season of next season
        int startYear;
        if (month >= 1 && month <= 6) {
            // Jan-June: Still in previous season (e.g., Jan 2026 is in 2025-2026 season)
            startYear = year - 1;
        } else if (month >= 10) {
            // Oct-Dec: New season has started (e.g., Oct 2025 is in 2025-2026 season)
            startYear = year;
        } else {
            // July-Sept: Off-season, but prepare for next season
            startYear = year;
        }

        int endYear = startYear + 1;
        return String.format("%d%d", startYear, endYear);
    }

    /**
     * Validate if a season ID is in valid format and within reasonable bounds.
     *
     * @param seasonId Season ID to validate (e.g., "20252026")
     * @return true if valid, false otherwise
     */
    public static boolean isValidSeasonId(String seasonId) {
        if (seasonId == null || seasonId.length() != 8) {
            return false;
        }

        try {
            int startYear = Integer.parseInt(seasonId.substring(0, 4));
            int endYear = Integer.parseInt(seasonId.substring(4, 8));

            // End year must be exactly 1 year after start year
            if (endYear != startYear + 1) {
                return false;
            }

            // Check minimum bound
            if (startYear < MIN_SEASON_START_YEAR) {
                return false;
            }

            // Check maximum bound (current season + 1 for future planning)
            String currentSeasonId = getCurrentSeasonId();
            int currentStartYear = Integer.parseInt(currentSeasonId.substring(0, 4));
            if (startYear > currentStartYear + 1) {
                return false;
            }

            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Convert a year to season ID.
     * For example: 2025 -> 20252026 (the season starting in 2025)
     *
     * @param year Starting year of the season
     * @return Season ID in format YYYYYYYY
     */
    public static String yearToSeasonId(int year) {
        return String.format("%d%d", year, year + 1);
    }

    /**
     * Extract the start year from a season ID.
     * For example: 20252026 -> 2025
     *
     * @param seasonId Season ID in format YYYYYYYY
     * @return Start year of the season
     */
    public static int getStartYear(String seasonId) {
        if (!isValidSeasonId(seasonId)) {
            throw new IllegalArgumentException("Invalid season ID: " + seasonId);
        }
        return Integer.parseInt(seasonId.substring(0, 4));
    }

    /**
     * Format a season ID into human-readable format.
     * For example: 20252026 -> "2025-2026"
     *
     * @param seasonId Season ID in format YYYYYYYY
     * @return Human-readable season string
     */
    public static String formatSeason(String seasonId) {
        if (!isValidSeasonId(seasonId)) {
            return "Invalid Season";
        }
        String startYear = seasonId.substring(0, 4);
        String endYear = seasonId.substring(4, 8);
        return startYear + "-" + endYear;
    }
}
