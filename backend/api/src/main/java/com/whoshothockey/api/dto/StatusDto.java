package com.whoshothockey.api.dto;

/**
 * DTO for the site footer.
 *
 * @param statsUpdatedAt when the data job last finished syncing current-season stats, as an ISO-8601
 *                       UTC instant; null before the first sync has finished
 */
public record StatusDto(String statsUpdatedAt) {}
