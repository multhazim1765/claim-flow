package com.claire.claims.web;

import com.claire.claims.common.ApiExceptions.BusinessRuleException;
import com.claire.claims.domain.RemittanceBatchStatus;
import com.claire.claims.dto.ClaimDtos.PageResponse;
import com.claire.claims.dto.RemittanceDtos.*;
import com.claire.claims.service.remittance.RemittanceIngestService;
import com.claire.claims.service.remittance.SyntheticEra835Parser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

@RestController
public class RemittanceController {

    private final RemittanceIngestService ingestService;
    private final SyntheticEra835Parser parser;
    private final ObjectMapper objectMapper;

    public RemittanceController(RemittanceIngestService ingestService,
                                SyntheticEra835Parser parser) {
        this.ingestService = ingestService;
        this.parser = parser;
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        this.objectMapper = mapper;
    }

    /**
     * Ingests a synthetic ERA 835 remittance batch (multipart file or JSON).
     * Validates headers, matches claims, detects duplicates, and stages vouchers.
     * STRICT PHASE A: No updates to claim financial balances or status transitions.
     * Restricted to ADMIN and BILLER.
     */
    @PostMapping(value = "/api/edi/835/import", consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE})
    @PreAuthorize("hasAnyRole('ADMIN','BILLER')")
    public ResponseEntity<RemittanceBatchDetailResponse> import835(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @Valid @RequestBody(required = false) ImportRemittanceRequest jsonRequest) {

        ImportRemittanceRequest request;
        String rawPayload = null;

        if (file != null && !file.isEmpty()) {
            try {
                byte[] bytes = file.getBytes();
                String content = new String(bytes, StandardCharsets.UTF_8).trim();
                rawPayload = content.length() > 1000 ? content.substring(0, 1000) + "... [truncated]" : content;

                if (content.startsWith("{")) {
                    request = objectMapper.readValue(content, ImportRemittanceRequest.class);
                } else {
                    request = parser.parseX12(content);
                }
            } catch (BusinessRuleException e) {
                throw e;
            } catch (Exception e) {
                throw new BusinessRuleException("Failed to parse uploaded remittance file: " + e.getMessage());
            }
        } else if (jsonRequest != null) {
            request = jsonRequest;
            try {
                rawPayload = objectMapper.writeValueAsString(jsonRequest);
            } catch (Exception ignored) { }
        } else {
            throw new BusinessRuleException("No remittance content provided. Please upload a file or submit a JSON payload.");
        }

        RemittanceBatchDetailResponse response = ingestService.ingest(request, rawPayload);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Paginated list of ingested remittance batches.
     * Accessible by ADMIN, BILLER, and VIEWER.
     */
    @GetMapping("/api/remittances")
    @PreAuthorize("hasAnyRole('ADMIN','BILLER','VIEWER')")
    public PageResponse<RemittanceBatchListItem> list(
            @RequestParam(required = false) Long payerId,
            @RequestParam(required = false) RemittanceBatchStatus status,
            @PageableDefault(size = 20, sort = "importedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ingestService.search(payerId, status, pageable);
    }

    /**
     * Single remittance batch details including staged vouchers and exceptions.
     * Accessible by ADMIN, BILLER, and VIEWER.
     */
    @GetMapping("/api/remittances/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','BILLER','VIEWER')")
    public RemittanceBatchDetailResponse getById(@PathVariable Long id) {
        return ingestService.getById(id);
    }

    /**
     * Paginated list of unmatched or out-of-balance remittance claim exceptions.
     * Accessible by ADMIN, BILLER, and VIEWER.
     */
    @GetMapping("/api/remittances/exceptions")
    @PreAuthorize("hasAnyRole('ADMIN','BILLER','VIEWER')")
    public PageResponse<RemittanceExceptionResponse> listExceptions(
            @RequestParam(required = false) Long remittanceId,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ingestService.listExceptions(remittanceId, status, pageable);
    }
}

