package com.claire.claims.web;

import com.claire.claims.dto.AppealDtos.*;
import com.claire.claims.service.AppealService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class AppealController {

    private final AppealService appealService;

    public AppealController(AppealService appealService) {
        this.appealService = appealService;
    }

    /**
     * Files a formal multi-level appeal on a denied claim.
     * Drives DENIED -> APPEALED.
     * Restricted to ADMIN and BILLER.
     */
    @PostMapping("/api/claims/{id}/appeal")
    @PreAuthorize("hasAnyRole('ADMIN','BILLER')")
    public ResponseEntity<AppealResponse> fileAppeal(
            @PathVariable Long id,
            @Valid @RequestBody FileAppealRequest request) {
        AppealResponse response = appealService.fileAppeal(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Returns full appeal history (Levels 1..3) for a claim.
     * Accessible by ADMIN, BILLER, and VIEWER.
     */
    @GetMapping("/api/claims/{id}/appeals")
    @PreAuthorize("hasAnyRole('ADMIN','BILLER','VIEWER')")
    public List<AppealResponse> listAppeals(@PathVariable Long id) {
        return appealService.listAppeals(id);
    }

    /**
     * Returns appeal details by appeal ID.
     * Accessible by ADMIN, BILLER, and VIEWER.
     */
    @GetMapping("/api/appeals/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','BILLER','VIEWER')")
    public AppealResponse getById(@PathVariable Long id) {
        return appealService.getById(id);
    }

    /**
     * Records the payer's decision (OVERTURNED, PARTIAL, UPHELD, WITHDRAWN) on an appeal.
     * Restricted to ADMIN and BILLER.
     */
    @PostMapping("/api/appeals/{id}/outcome")
    @PreAuthorize("hasAnyRole('ADMIN','BILLER')")
    public ResponseEntity<AppealResponse> recordOutcome(
            @PathVariable Long id,
            @Valid @RequestBody RecordOutcomeRequest request) {
        AppealResponse response = appealService.recordOutcome(id, request);
        return ResponseEntity.ok(response);
    }
}

