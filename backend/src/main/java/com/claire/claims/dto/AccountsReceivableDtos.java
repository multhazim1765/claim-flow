package com.claire.claims.dto;

import com.claire.claims.domain.ClaimStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public final class AccountsReceivableDtos {

    private AccountsReceivableDtos() {}

    public record AccountsReceivableSummaryResponse(
            BigDecimal grossBilledCharges,
            BigDecimal legacyOpeningPayments,
            BigDecimal postedPayerPayments,
            BigDecimal payerPaymentReversals,
            BigDecimal netRecordedPayerPayments,
            BigDecimal verifiedContractualWriteOffs,
            BigDecimal adjudicatedPayerAr,
            BigDecimal patientAr,
            BigDecimal disputedAr,
            BigDecimal unadjudicatedSubmittedExposure,
            BigDecimal rejectedClaimExposure,
            BigDecimal draftClaimExposure,
            BigDecimal voidClaimExposure,
            BigDecimal legacyUnclassifiedBalances,
            BigDecimal totalCollectibleAr,
            BigDecimal totalOutstandingExposure,
            boolean isBalanced,
            BigDecimal balanceDiscrepancy,
            int totalClaimsCount,
            OffsetDateTime generatedAt
    ) {}

    public record ClaimArBreakdownResponse(
            Long claimId,
            String claimNumber,
            ClaimStatus status,
            BigDecimal totalCharge,
            BigDecimal allowedAmount,
            BigDecimal paidAmount,
            BigDecimal patientResponsibility,
            BigDecimal contractualAdjustment,
            BigDecimal remainingBalance,
            BigDecimal adjudicatedPayerAr,
            BigDecimal patientAr,
            BigDecimal disputedExposure,
            BigDecimal unadjudicatedExposure,
            String legacyDiscrepancyNotes,
            int ledgerEntriesCount
    ) {}
}

