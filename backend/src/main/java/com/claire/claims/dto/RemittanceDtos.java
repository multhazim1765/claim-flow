package com.claire.claims.dto;

import com.claire.claims.domain.AdjustmentGroupCode;
import com.claire.claims.domain.RemittanceBatchStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public final class RemittanceDtos {

    private RemittanceDtos() { }

    // ----- Write Models ---------------------------------------------------

    public record ImportRemittanceRequest(
            Long payerId,
            String payerCode,

            @NotBlank(message = "checkNumber is required")
            @Size(max = 50, message = "checkNumber must not exceed 50 characters")
            String checkNumber,

            @NotNull(message = "checkDate is required")
            LocalDate checkDate,

            @Size(max = 10, message = "paymentMethod must not exceed 10 characters")
            String paymentMethod,

            @NotNull(message = "totalPaid is required")
            @Digits(integer = 10, fraction = 2)
            BigDecimal totalPaid,

            @NotEmpty(message = "claims list must not be empty")
            @Valid
            List<RemittanceClaimInput> claims
    ) { }

    public record RemittanceClaimInput(
            @NotBlank(message = "claimNumber is required")
            @Size(max = 24, message = "claimNumber must not exceed 24 characters")
            String claimNumber,

            @Size(max = 50, message = "payerIcn must not exceed 50 characters")
            String payerIcn,

            @NotBlank(message = "clpStatusCode is required")
            @Size(max = 5, message = "clpStatusCode must not exceed 5 characters")
            String clpStatusCode,

            @NotNull(message = "billedAmount is required")
            @Digits(integer = 10, fraction = 2)
            BigDecimal billedAmount,

            @NotNull(message = "allowedAmount is required")
            @Digits(integer = 10, fraction = 2)
            BigDecimal allowedAmount,

            @NotNull(message = "paidAmount is required")
            @Digits(integer = 10, fraction = 2)
            BigDecimal paidAmount,

            @Digits(integer = 10, fraction = 2)
            BigDecimal patientResponsibility,

            String entryType,

            @Valid
            List<RemittanceLineInput> lines,

            @Valid
            List<RemittanceAdjustmentInput> adjustments
    ) { }

    public record RemittanceLineInput(
            String cptCode,
            Integer lineNumber,
            BigDecimal billedAmount,
            BigDecimal allowedAmount,
            BigDecimal paidAmount,

            @Valid
            List<RemittanceAdjustmentInput> adjustments
    ) { }

    public record RemittanceAdjustmentInput(
            @NotNull(message = "groupCode is required")
            AdjustmentGroupCode groupCode,

            @NotBlank(message = "reasonCode is required")
            @Size(max = 10, message = "reasonCode must not exceed 10 characters")
            String reasonCode,

            @Size(max = 10, message = "remarkCode must not exceed 10 characters")
            String remarkCode,

            @NotNull(message = "amount is required")
            @Digits(integer = 10, fraction = 2)
            BigDecimal amount
    ) { }

    // ----- Read Models ----------------------------------------------------

    public record RemittanceBatchListItem(
            Long id,
            String batchControlNumber,
            Long payerId,
            String payerName,
            String paymentMethod,
            String checkNumber,
            LocalDate checkDate,
            BigDecimal totalPaid,
            int claimsCount,
            int matchedCount,
            int exceptionCount,
            RemittanceBatchStatus status,
            String importedBy,
            OffsetDateTime importedAt
    ) { }

    public record RemittanceAdjustmentResponse(
            Long id,
            Long claimLineId,
            AdjustmentGroupCode groupCode,
            String reasonCode,
            String remarkCode,
            BigDecimal amount,
            String entryType
    ) { }

    public record RemittanceVoucherResponse(
            Long id,
            Long claimId,
            Integer sequenceNo,
            String claimNumber,
            String payerIcn,
            String clpStatusCode,
            BigDecimal billedAmount,
            BigDecimal allowedAmount,
            BigDecimal paidAmount,
            BigDecimal patientResponsibility,
            BigDecimal contractualAdj,
            BigDecimal otherAdj,
            String entryType,
            String balanceStatus,
            String postingStatus,
            OffsetDateTime postedAt,
            String postedBy,
            List<RemittanceAdjustmentResponse> adjustments
    ) { }

    public record RemittanceExceptionResponse(
            Long id,
            Long remittanceId,
            Long voucherId,
            String claimNumber,
            String patientName,
            LocalDate serviceDate,
            BigDecimal billedAmount,
            BigDecimal paidAmount,
            String exceptionType,
            String errorMessage,
            String status,
            OffsetDateTime resolvedAt,
            String resolvedBy
    ) { }

    public record RemittanceBatchDetailResponse(
            Long id,
            String batchControlNumber,
            Long payerId,
            String payerName,
            String paymentMethod,
            String checkNumber,
            LocalDate checkDate,
            BigDecimal totalPaid,
            int claimsCount,
            int matchedCount,
            int exceptionCount,
            RemittanceBatchStatus status,
            String importedBy,
            OffsetDateTime importedAt,
            List<RemittanceVoucherResponse> vouchers,
            List<RemittanceExceptionResponse> exceptions
    ) { }
}

