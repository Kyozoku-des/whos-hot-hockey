package com.whoshothockey.api.controller;

import com.whoshothockey.api.dto.StatusDto;
import com.whoshothockey.api.service.StatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for data freshness.
 */
@RestController
@RequestMapping("/api/status")
@Tag(name = "Status", description = "How current the stored data is")
@RequiredArgsConstructor
public class StatusController {

    private final StatusService statusService;

    @GetMapping
    @Operation(summary = "Get data freshness",
               description = "Returns when the data job last finished syncing current-season stats")
    public ResponseEntity<StatusDto> getStatus() {
        return ResponseEntity.ok(statusService.getStatus());
    }
}
