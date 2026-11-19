package com.claire.claims.dto;

import com.claire.claims.domain.DenialCategory;
import com.claire.claims.domain.DenialStatus;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;

public final class DenialDtos {

    private DenialDtos() { }

    // ----- write models ---------------------------------------------------

    public record CreateDenialRequest(
            @NotNull(message = "claimId is required")
            Long claimId,

            Long payerId,

            @NotBlank(message = "denialCode (CARC) is required")
            @Size(max = 10, message = "denialCode must be at most 10 characters")
            String denialCode,

            @Size(max = 10, message = "remittanceCode must be at most 10 characters")
            String remittanceCode,

            @NotNull(message = "denialCategory is required")
            DenialCategory denialCategory,

            @NotBlank(message = "reason is required")
            @Size(max = 500, message = "reason must not exceed 500 characters")
            String reason,

            @NotNull(message = "denialDate is required")
            LocalDate denialDate,

            @NotNull(message = "appealDeadline is required")
            LocalDate appealDeadline,

            @NotNull(message = "amount is required")
            @DecimalMin(value = "0.00", message = "amount must be non-negative")
            @Digits(integer = 10, fraction = 2)
            BigDecimal amount
    ) { }

    // ----- read models ----------------------------------------------------

    public record DenialWorklistItem(
            Long id,
            Long claimId,
            String claimNumber,
            String patientName,
            String patientMrn,
            Long payerId,
            String payerName,
            String denialCode,
            String remittanceCode,
            DenialCategory denialCategory,
            String reason,
            LocalDate denialDate,
            LocalDate appealDeadline,
            long daysRemaining,
            String urgency,
            BigDecimal amount,
            DenialStatus status,
            String createdBy,
            OffsetDateTime createdAt
    ) { }

    public record CategoryBreakdown(
            DenialCategory category,
            String description,
            long count,
            BigDecimal amount
    ) { }

    public record DenialSummaryResponse(
            long totalOpenDenials,
            BigDecimal totalOpenDeniedAmount,
            long criticalDeadlines,
            long warningDeadlines,
            long normalDeadlines,
            long overdueDeadlines,
            Map<String, CategoryBreakdown> byCategory
    ) { }

    public record DenialResponse(
            Long id,
            Long claimId,
            String claimNumber,
            Long payerId,
            String payerName,
            String denialCode,
            String remittanceCode,
            DenialCategory denialCategory,
            String reason,
            LocalDate denialDate,
            LocalDate appealDeadline,
            long daysRemaining,
            String urgency,
            BigDecimal amount,
            DenialStatus status,
            String createdBy,
            OffsetDateTime createdAt
    ) { }
}

