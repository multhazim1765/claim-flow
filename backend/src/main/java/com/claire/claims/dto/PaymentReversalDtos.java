package com.claire.claims.dto;

import com.claire.claims.domain.ClaimStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public final class PaymentReversalDtos {

    private PaymentReversalDtos() {}

    public record PaymentReversalRequest(
            @NotNull(message = "originalLedgerId is required")
            Long originalLedgerId,

            @NotBlank(message = "reversalReason is required")
            @Size(max = 500, message = "reversalReason must not exceed 500 characters")
            String reversalReason,

            @Size(max = 50, message = "checkNumber must not exceed 50 characters")
            String checkNumber,

            LocalDate checkDate,

            @Size(max = 500, message = "notes must not exceed 500 characters")
            String notes
    ) {}

    public record PaymentReversalResponse(
            Long claimId,
            String claimNumber,
            Long originalLedgerId,
            BigDecimal reversedPaymentAmount,
            BigDecimal reversedContractualAdjustment,
            BigDecimal remainingPaidAmount,
            BigDecimal remainingContractualAdjustment,
            ClaimStatus previousClaimStatus,
            ClaimStatus updatedClaimStatus,
            Long reversalLedgerEntryId,
            Long coReversalLedgerEntryId,
            String reversalReason,
            OffsetDateTime postedAt,
            String postedBy
    ) {}
}

