package com.claire.claims.phase2;

import com.claire.claims.common.ApiExceptions.NotImplementedYetException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * Phase 2 surface: endpoints that exist, are documented, and deliberately are
 * not implemented.
 *
 * Every one returns 501 with an X-Phase: 2 header and a pointer into
 * docs/PHASE2_HANDOFF.md. Nothing is silently missing - the gaps are
 * discoverable from Swagger UI, which is what makes this a clean handoff to
 * the agent that picks up Phase 2.
 */
@RestController
public class Phase2Controller {

    // ----- 2.1 EDI 837P export -------------------------------------------

    /**
     * Generates an X12 837 Professional file for a batch of claims.
     * Spec: docs/PHASE2_HANDOFF.md section 2.1
     */
    @PostMapping("/api/edi/837p/export")
    @PreAuthorize("hasAnyRole('ADMIN','BILLER')")
    public Map<String, Object> export837p(@RequestBody(required = false) Map<String, Object> body) {
        throw new NotImplementedYetException("EDI 837P claim export", "2.1");
    }

    // ----- 2.2 ERA / 835 remittance ingest implemented in RemittanceController -----

    // ----- 2.3 Eligibility (270/271) implemented in EligibilityController -----

    // ----- 2.4 Denial management and appeals -----------------------------

    // ----- 2.5 Attachments ------------------------------------------------

    /**
     * Uploads a supporting document against a claim.
     * Spec: docs/PHASE2_HANDOFF.md section 2.5
     */
    @PostMapping("/api/claims/{id}/attachments")
    @PreAuthorize("hasAnyRole('ADMIN','BILLER')")
    public Map<String, Object> uploadAttachment(@PathVariable Long id,
                                                @RequestParam(value = "file", required = false) MultipartFile file) {
        throw new NotImplementedYetException("Claim document attachments", "2.5");
    }

    @GetMapping("/api/claims/{id}/attachments")
    public List<Object> listAttachments(@PathVariable Long id) {
        throw new NotImplementedYetException("Claim document attachments", "2.5");
    }

    // ----- 2.6 Analytics implemented in RevenueCycleAnalyticsController -----
}
