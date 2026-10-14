package com.whoshot.nhl.api.service;

import com.whoshot.nhl.api.dto.StatusDto;
import com.whoshot.nhl.domain.entity.SyncStatus;
import com.whoshot.nhl.domain.repository.SyncStatusRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Service reporting how current the stored data is.
 */
@Service
@RequiredArgsConstructor
public class StatusService {

    private final SyncStatusRepository syncStatusRepository;

    public StatusDto getStatus() {
        return new StatusDto(syncStatusRepository.findById(SyncStatus.STATS)
                .map(status -> status.getLastSuccessAt().toString())
                .orElse(null));
    }
}
