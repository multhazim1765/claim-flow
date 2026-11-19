package com.claire.claims.dto;

import com.claire.claims.domain.ClaimStatus;
import com.claire.claims.domain.PaymentLedgerEntryType;
import com.claire.claims.domain.RemittanceBatchStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public class PaymentPostingDtos {

    public record VoucherPostingResponse(
            Long voucherId,
            Long claimId,
            String claimNumber,
            ClaimStatus previousStatus,
            ClaimStatus newStatus,
            BigDecimal postedAmount,
            BigDecimal patientResponsibility,
            BigDecimal contractualWriteOff,
            BigDecimal cumulativeClaimPaid,
            boolean isFutureDated,
            String settlementNote,
            String postedBy,
            OffsetDateTime postedAt
    ) { }

    public record BatchPostingResponse(
            Long remittanceId,
            String batchControlNumber,
            int totalVouchers,
            int postedCount,
            RemittanceBatchStatus batchStatus,
            boolean isFutureDated,
            String settlementNote,
            OffsetDateTime postedAt,
            List<VoucherPostingResponse> vouchers
    ) { }

    public record PaymentLedgerResponse(
            Long id,
            Long claimId,
            Long voucherId,
            Long remittanceId,
            PaymentLedgerEntryType entryType,
            BigDecimal amount,
            String payerIcn,
            String checkNumber,
            LocalDate checkDate,
            String notes,
            String postedBy,
            OffsetDateTime postedAt
    ) { }
}

