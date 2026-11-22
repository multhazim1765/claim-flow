package com.claire.claims.web;

import com.claire.claims.dto.Edi837pDtos.*;
import com.claire.claims.service.edi.Edi837pExportService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/edi/837p")
public class Edi837pExportController {

    private final Edi837pExportService exportService;

    public Edi837pExportController(Edi837pExportService exportService) {
        this.exportService = exportService;
    }

    /**
     * Pre-export validation of selected claims without financial or state mutations.
     */
    @PostMapping("/validate")
    @PreAuthorize("hasAnyRole('ADMIN','BILLER')")
    public ResponseEntity<EdiValidationResponse> validateClaims(@Valid @RequestBody ValidateClaimsRequest request) {
        EdiValidationResponse response = exportService.validateClaims(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Generates a synthetic X12 837P claim file for an eligible batch of claims.
     */
    @PostMapping("/export")
    @PreAuthorize("hasAnyRole('ADMIN','BILLER')")
    public ResponseEntity<EdiExportBatchDetailResponse> export837p(
            @Valid @RequestBody Export837pRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        String username = jwt != null ? jwt.getSubject() : "anonymous";
        EdiExportBatchDetailResponse response = exportService.export837p(request, username);
        return ResponseEntity.ok(response);
    }

    /**
     * Paginated history of generated EDI 837P export batches.
     */
    @GetMapping("/exports")
    @PreAuthorize("hasAnyRole('ADMIN','BILLER','VIEWER')")
    public ResponseEntity<Page<EdiExportBatchSummary>> getExports(
            @RequestParam(required = false) Long payerId,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<EdiExportBatchSummary> response = exportService.getExports(payerId, status, pageable);
        return ResponseEntity.ok(response);
    }

    /**
     * Detailed manifest of a specific EDI 837P export batch.
     */
    @GetMapping("/exports/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','BILLER','VIEWER')")
    public ResponseEntity<EdiExportBatchDetailResponse> getExportById(@PathVariable Long id) {
        EdiExportBatchDetailResponse response = exportService.getExportById(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Downloads the original, immutable synthetic X12 837P text file.
     */
    @GetMapping("/exports/{id}/download")
    @PreAuthorize("hasAnyRole('ADMIN','BILLER','VIEWER')")
    public ResponseEntity<byte[]> downloadExportFile(@PathVariable Long id) {
        Edi837pExportService.ExportFileResult fileResult = exportService.getExportFile(id);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileResult.fileName() + "\"")
                .header(HttpHeaders.CONTENT_TYPE, "application/octet-stream; charset=UTF-8")
                .header("X-Content-Type-Options", "nosniff")
                .header("X-Content-SHA256", fileResult.contentHash())
                .body(fileResult.payload().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Executive KPI metrics for EDI 837P export operations.
     */
    @GetMapping("/kpis")
    @PreAuthorize("hasAnyRole('ADMIN','BILLER','VIEWER')")
    public ResponseEntity<EdiExportKpis> getKpis() {
        return ResponseEntity.ok(exportService.getKpis());
    }

    /**
     * Eligible claims worklist (SUBMITTED status) available for batch export.
     */
    @GetMapping("/eligible-claims")
    @PreAuthorize("hasAnyRole('ADMIN','BILLER','VIEWER')")
    public ResponseEntity<Page<EligibleClaimDto>> getEligibleClaims(
            @RequestParam(required = false) Long payerId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(exportService.getEligibleClaims(payerId, pageable));
    }
}

