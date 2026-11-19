package com.claire.claims.web;

import com.claire.claims.dto.EligibilityDtos.EligibilityCheckRequest;
import com.claire.claims.dto.EligibilityDtos.EligibilityCheckResponse;
import com.claire.claims.dto.EligibilityDtos.EligibilityHistoryItem;
import com.claire.claims.service.eligibility.EligibilityService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/eligibility")
public class EligibilityController {

    private final EligibilityService eligibilityService;

    public EligibilityController(EligibilityService eligibilityService) {
        this.eligibilityService = eligibilityService;
    }

    /**
     * Real-time 270/271 eligibility verification against a payer.
     * Enforces a 24-hour cache unless forceRefresh is true.
     */
    @PostMapping("/check")
    @PreAuthorize("hasAnyRole('ADMIN','BILLER')")
    public EligibilityCheckResponse check(@Valid @RequestBody EligibilityCheckRequest request) {
        return eligibilityService.checkEligibility(request);
    }

    /**
     * Audit trail of historical eligibility verifications for a patient.
     */
    @GetMapping("/history")
    @PreAuthorize("hasAnyRole('ADMIN','BILLER','VIEWER')")
    public List<EligibilityHistoryItem> history(@RequestParam Long patientId) {
        return eligibilityService.getHistory(patientId);
    }

    /**
     * Retrieves the most recent verification for a patient, optionally filtered by payer.
     */
    @GetMapping("/patient/{patientId}/latest")
    @PreAuthorize("hasAnyRole('ADMIN','BILLER','VIEWER')")
    public ResponseEntity<EligibilityCheckResponse> latest(
            @PathVariable Long patientId,
            @RequestParam(required = false) Long payerId) {
        return eligibilityService.getLatest(patientId, payerId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }
}

