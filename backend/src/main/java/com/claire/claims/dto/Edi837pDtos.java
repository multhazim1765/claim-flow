package com.claire.claims.dto;

import jakarta.validation.constraints.NotEmpty;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public final class Edi837pDtos {

    private Edi837pDtos() {}

    public record ValidateClaimsRequest(
            @NotEmpty(message = "claimIds must not be empty")
            List<Long> claimIds,
            Long payerId
    ) {}

    public record ClaimValidationResult(
            Long claimId,
            String claimNumber,
            boolean valid,
            List<String> errors,
            BigDecimal totalCharge,
            String patientName,
            String payerName
    ) {}

    public record EdiValidationResponse(
            boolean allValid,
            int validCount,
            int errorCount,
            BigDecimal totalCharge,
            List<ClaimValidationResult> results
    ) {}

    public record Export837pRequest(
            @NotEmpty(message = "claimIds must not be empty")
            List<Long> claimIds,
            Long payerId,
            Boolean allowRebill,
            String notes
    ) {}

    public record ExportedClaimItem(
            Long claimId,
            String claimNumber,
            BigDecimal chargeAmount,
            int sequence,
            OffsetDateTime exportedAt
    ) {}

    public record EdiExportBatchSummary(
            Long id,
            String batchNumber,
            Long payerId,
            String payerName,
            String exportStatus,
            int claimCount,
            BigDecimal totalBilledAmount,
            String fileName,
            OffsetDateTime exportedAt,
            String createdBy
    ) {}

    public record EdiExportBatchDetailResponse(
            Long id,
            String batchNumber,
            Long payerId,
            String payerName,
            String interchangeControlNumber,
            String groupControlNumber,
            String transactionControlNumber,
            String exportStatus,
            int claimCount,
            BigDecimal totalBilledAmount,
            String fileName,
            String contentHash,
            OffsetDateTime exportedAt,
            String createdBy,
            String validationSummary,
            List<ExportedClaimItem> claims,
            String downloadUrl
    ) {}

    public record EdiExportKpis(
            long eligibleClaimsCount,
            long totalBatchesCount,
            long totalExportedClaimsCount,
            BigDecimal totalAmountExported,
            long validationFailuresCount
    ) {}

    public record EligibleClaimDto(
            Long id,
            String claimNumber,
            String patientName,
            Long patientId,
            String payerName,
            Long payerId,
            String providerName,
            Long providerId,
            LocalDate serviceDateFrom,
            LocalDate serviceDateTo,
            String status,
            BigDecimal totalCharge,
            boolean previouslyExported,
            OffsetDateTime lastExportedAt
    ) {}
}

