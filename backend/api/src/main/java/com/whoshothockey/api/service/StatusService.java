package com.whoshothockey.api.service;

import com.whoshothockey.api.dto.StatusDto;
import com.whoshothockey.domain.entity.SyncStatus;
import com.whoshothockey.domain.repository.SyncStatusRepository;
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
