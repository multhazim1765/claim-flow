package com.claire.claims.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public final class RemittanceExceptionDtos {

    private RemittanceExceptionDtos() {}

    public record ExceptionResolutionRequest(
            @NotBlank(message = "action is required")
            @Size(max = 50, message = "action must not exceed 50 characters")
            String action,

            Long matchedClaimId,

            @NotBlank(message = "notes is required")
            @Size(max = 500, message = "notes must not exceed 500 characters")
            String notes
    ) {}

    public record RemittanceExceptionResponse(
            Long id,
            Long remittanceId,
            String batchControlNumber,
            Long voucherId,
            String claimNumber,
            String patientName,
            LocalDate serviceDate,
            BigDecimal billedAmount,
            BigDecimal paidAmount,
            String exceptionType,
            String errorMessage,
            String status,
            String resolutionAction,
            String resolutionNotes,
            Long matchedClaimId,
            String matchedClaimNumber,
            OffsetDateTime resolvedAt,
            String resolvedBy
    ) {}
}

