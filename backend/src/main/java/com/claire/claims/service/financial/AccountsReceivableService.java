package com.claire.claims.service.financial;

import com.claire.claims.common.ApiExceptions.NotFoundException;
import com.claire.claims.domain.Claim;
import com.claire.claims.domain.ClaimStatus;
import com.claire.claims.domain.PaymentLedgerEntry;
import com.claire.claims.domain.PaymentLedgerEntryType;
import com.claire.claims.dto.AccountsReceivableDtos.AccountsReceivableSummaryResponse;
import com.claire.claims.dto.AccountsReceivableDtos.ClaimArBreakdownResponse;
import com.claire.claims.repository.ClaimRepository;
import com.claire.claims.repository.PaymentLedgerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class AccountsReceivableService {

    private final ClaimRepository claimRepository;
    private final PaymentLedgerRepository ledgerRepository;

    public AccountsReceivableService(ClaimRepository claimRepository,
                                     PaymentLedgerRepository ledgerRepository) {
        this.claimRepository = claimRepository;
        this.ledgerRepository = ledgerRepository;
    }

    public AccountsReceivableSummaryResponse getArSummary() {
        List<Claim> allClaims = claimRepository.findAll();

        BigDecimal legacyOpeningPayments = defaultZero(ledgerRepository.sumAmountByEntryType(PaymentLedgerEntryType.LEGACY_INITIAL_BALANCE));
        BigDecimal postedPayerPayments = defaultZero(ledgerRepository.sumAmountByEntryType(PaymentLedgerEntryType.PAYER_PAYMENT));
        BigDecimal payerPaymentReversals = defaultZero(ledgerRepository.sumAmountByEntryType(PaymentLedgerEntryType.PAYER_REVERSAL));
        BigDecimal netRecordedPayerPayments = legacyOpeningPayments.add(postedPayerPayments).add(payerPaymentReversals).setScale(2, RoundingMode.HALF_UP);

        BigDecimal writeOffs = defaultZero(ledgerRepository.sumAmountByEntryType(PaymentLedgerEntryType.CONTRACTUAL_WRITE_OFF));
        BigDecimal writeOffReversals = defaultZero(ledgerRepository.sumAmountByEntryType(PaymentLedgerEntryType.CONTRACTUAL_ADJUSTMENT_REVERSAL));
        BigDecimal verifiedContractualWriteOffs = writeOffs.add(writeOffReversals).setScale(2, RoundingMode.HALF_UP);

        BigDecimal grossBilledCharges = BigDecimal.ZERO;
        BigDecimal patientAr = BigDecimal.ZERO;
        BigDecimal disputedAr = BigDecimal.ZERO;
        BigDecimal unadjudicatedSubmittedExposure = BigDecimal.ZERO;
        BigDecimal rejectedClaimExposure = BigDecimal.ZERO;
        BigDecimal draftClaimExposure = BigDecimal.ZERO;
        BigDecimal voidClaimExposure = BigDecimal.ZERO;
        BigDecimal adjudicatedPayerAr = BigDecimal.ZERO;
        BigDecimal legacyUnclassifiedBalances = BigDecimal.ZERO;

        for (Claim c : allClaims) {
            BigDecimal totalCharge = defaultZero(c.getTotalCharge());
            BigDecimal allowedAmount = defaultZero(c.getAllowedAmount());
            BigDecimal paidAmount = defaultZero(c.getPaidAmount());
            BigDecimal patientResp = defaultZero(c.getPatientResponsibility());
            BigDecimal contractualAdj = defaultZero(c.getContractualAdjustment());

            grossBilledCharges = grossBilledCharges.add(totalCharge);
            patientAr = patientAr.add(patientResp);

            ClaimStatus status = c.getStatus();
            if (status == ClaimStatus.DENIED || status == ClaimStatus.APPEALED) {
                disputedAr = disputedAr.add(totalCharge);
            } else if (status == ClaimStatus.SUBMITTED) {
                unadjudicatedSubmittedExposure = unadjudicatedSubmittedExposure.add(totalCharge);
            } else if (status == ClaimStatus.REJECTED) {
                rejectedClaimExposure = rejectedClaimExposure.add(totalCharge);
            } else if (status == ClaimStatus.DRAFT) {
                draftClaimExposure = draftClaimExposure.add(totalCharge);
            } else if (status == ClaimStatus.VOID) {
                voidClaimExposure = voidClaimExposure.add(totalCharge);
            } else if (status == ClaimStatus.ACCEPTED || status == ClaimStatus.PARTIALLY_PAID) {
                if (allowedAmount.signum() > 0) {
                    BigDecimal payerObligation = allowedAmount.subtract(paidAmount).subtract(patientResp);
                    if (payerObligation.signum() > 0) {
                        adjudicatedPayerAr = adjudicatedPayerAr.add(payerObligation);
                    }
                }
            }

            // Detect legacy inconsistencies
            if (status == ClaimStatus.PAID && patientResp.signum() > 0) {
                legacyUnclassifiedBalances = legacyUnclassifiedBalances.add(patientResp);
            }
            if (totalCharge.compareTo(allowedAmount) > 0 && allowedAmount.signum() > 0
                    && contractualAdj.signum() == 0 && paidAmount.signum() > 0) {
                BigDecimal discountVariance = totalCharge.subtract(allowedAmount);
                legacyUnclassifiedBalances = legacyUnclassifiedBalances.add(discountVariance);
            }
        }

        grossBilledCharges = grossBilledCharges.setScale(2, RoundingMode.HALF_UP);
        patientAr = patientAr.setScale(2, RoundingMode.HALF_UP);
        disputedAr = disputedAr.setScale(2, RoundingMode.HALF_UP);
        unadjudicatedSubmittedExposure = unadjudicatedSubmittedExposure.setScale(2, RoundingMode.HALF_UP);
        rejectedClaimExposure = rejectedClaimExposure.setScale(2, RoundingMode.HALF_UP);
        draftClaimExposure = draftClaimExposure.setScale(2, RoundingMode.HALF_UP);
        voidClaimExposure = voidClaimExposure.setScale(2, RoundingMode.HALF_UP);
        adjudicatedPayerAr = adjudicatedPayerAr.setScale(2, RoundingMode.HALF_UP);
        legacyUnclassifiedBalances = legacyUnclassifiedBalances.setScale(2, RoundingMode.HALF_UP);

        BigDecimal totalCollectibleAr = adjudicatedPayerAr.add(patientAr).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalOutstandingExposure = grossBilledCharges
                .subtract(netRecordedPayerPayments)
                .subtract(verifiedContractualWriteOffs)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal accountedSum = netRecordedPayerPayments.add(verifiedContractualWriteOffs).add(totalOutstandingExposure);
        BigDecimal balanceDiscrepancy = grossBilledCharges.subtract(accountedSum).setScale(2, RoundingMode.HALF_UP);
        boolean isBalanced = balanceDiscrepancy.compareTo(BigDecimal.ZERO) == 0;

        return new AccountsReceivableSummaryResponse(
                grossBilledCharges,
                legacyOpeningPayments,
                postedPayerPayments,
                payerPaymentReversals,
                netRecordedPayerPayments,
                verifiedContractualWriteOffs,
                adjudicatedPayerAr,
                patientAr,
                disputedAr,
                unadjudicatedSubmittedExposure,
                rejectedClaimExposure,
                draftClaimExposure,
                voidClaimExposure,
                legacyUnclassifiedBalances,
                totalCollectibleAr,
                totalOutstandingExposure,
                isBalanced,
                balanceDiscrepancy,
                allClaims.size(),
                OffsetDateTime.now()
        );
    }

    public ClaimArBreakdownResponse getClaimArBreakdown(Long claimId) {
        Claim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new NotFoundException("Claim", claimId));

        BigDecimal totalCharge = defaultZero(claim.getTotalCharge());
        BigDecimal allowedAmount = defaultZero(claim.getAllowedAmount());
        BigDecimal paidAmount = defaultZero(claim.getPaidAmount());
        BigDecimal patientResp = defaultZero(claim.getPatientResponsibility());
        BigDecimal contractualAdj = defaultZero(claim.getContractualAdjustment());

        BigDecimal remainingBalance = totalCharge.subtract(paidAmount).subtract(contractualAdj).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

        BigDecimal adjudicatedPayerAr = BigDecimal.ZERO;
        if ((claim.getStatus() == ClaimStatus.ACCEPTED || claim.getStatus() == ClaimStatus.PARTIALLY_PAID) && allowedAmount.signum() > 0) {
            BigDecimal payerObligation = allowedAmount.subtract(paidAmount).subtract(patientResp);
            if (payerObligation.signum() > 0) {
                adjudicatedPayerAr = payerObligation.setScale(2, RoundingMode.HALF_UP);
            }
        }

        BigDecimal patientAr = patientResp.setScale(2, RoundingMode.HALF_UP);
        BigDecimal disputedExposure = (claim.getStatus() == ClaimStatus.DENIED || claim.getStatus() == ClaimStatus.APPEALED)
                ? totalCharge : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal unadjudicatedExposure = (claim.getStatus() == ClaimStatus.SUBMITTED)
                ? totalCharge : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        List<String> notes = new ArrayList<>();
        if (claim.getStatus() == ClaimStatus.PAID && patientResp.signum() > 0) {
            notes.add("Legacy claim has status PAID but uncollected patient responsibility of $" + patientResp);
        }
        if (totalCharge.compareTo(allowedAmount) > 0 && allowedAmount.signum() > 0
                && contractualAdj.signum() == 0 && paidAmount.signum() > 0) {
            notes.add("Legacy claim has discount variance of $" + totalCharge.subtract(allowedAmount) + " without explicit write-off row");
        }
        String discrepancyNotes = notes.isEmpty() ? null : String.join("; ", notes);

        List<PaymentLedgerEntry> entries = ledgerRepository.findByClaimIdOrderByPostedAtAsc(claim.getId());

        return new ClaimArBreakdownResponse(
                claim.getId(),
                claim.getClaimNumber(),
                claim.getStatus(),
                totalCharge,
                allowedAmount,
                paidAmount,
                patientResp,
                contractualAdj,
                remainingBalance,
                adjudicatedPayerAr,
                patientAr,
                disputedExposure,
                unadjudicatedExposure,
                discrepancyNotes,
                entries.size()
        );
    }

    private BigDecimal defaultZero(BigDecimal val) {
        return val != null ? val : BigDecimal.ZERO;
    }
}

