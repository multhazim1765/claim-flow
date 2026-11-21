package com.claire.claims.service;

import com.claire.claims.domain.Claim;
import com.claire.claims.domain.ClaimStatus;
import com.claire.claims.domain.PaymentLedgerEntryType;
import com.claire.claims.dto.AccountsReceivableDtos.AccountsReceivableSummaryResponse;
import com.claire.claims.dto.AccountsReceivableDtos.ClaimArBreakdownResponse;
import com.claire.claims.repository.ClaimRepository;
import com.claire.claims.repository.PaymentLedgerRepository;
import com.claire.claims.service.financial.AccountsReceivableService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountsReceivableServiceTest {

    @Mock
    private ClaimRepository claimRepository;

    @Mock
    private PaymentLedgerRepository ledgerRepository;

    private AccountsReceivableService arService;

    @BeforeEach
    void setUp() {
        arService = new AccountsReceivableService(claimRepository, ledgerRepository);
    }

    @Test
    @DisplayName("Accounts Receivable Summary computes all 16 financial dimensions and reconciles balance")
    void testArSummaryCalculationsAndReconciliation() {
        // Mock Claims
        Claim c1 = new Claim();
        c1.setId(1L);
        c1.setStatus(ClaimStatus.PAID);
        c1.setTotalCharge(new BigDecimal("100.00"));
        c1.setAllowedAmount(new BigDecimal("80.00"));
        c1.setPaidAmount(new BigDecimal("80.00"));
        c1.setPatientResponsibility(BigDecimal.ZERO);
        c1.setContractualAdjustment(new BigDecimal("20.00"));

        Claim c2 = new Claim();
        c2.setId(2L);
        c2.setStatus(ClaimStatus.PARTIALLY_PAID);
        c2.setTotalCharge(new BigDecimal("500.00"));
        c2.setAllowedAmount(new BigDecimal("450.00"));
        c2.setPaidAmount(new BigDecimal("300.00"));
        c2.setPatientResponsibility(new BigDecimal("100.00"));
        c2.setContractualAdjustment(new BigDecimal("50.00")); // Payer owed = 450 - 300 - 100 = 50.00

        Claim c3 = new Claim();
        c3.setId(3L);
        c3.setStatus(ClaimStatus.SUBMITTED);
        c3.setTotalCharge(new BigDecimal("600.00"));

        Claim c4 = new Claim();
        c4.setId(4L);
        c4.setStatus(ClaimStatus.DENIED);
        c4.setTotalCharge(new BigDecimal("400.00"));

        Claim c5 = new Claim();
        c5.setId(5L);
        c5.setStatus(ClaimStatus.REJECTED);
        c5.setTotalCharge(new BigDecimal("200.00"));

        Claim c6 = new Claim();
        c6.setId(6L);
        c6.setStatus(ClaimStatus.DRAFT);
        c6.setTotalCharge(new BigDecimal("150.00"));

        // Legacy inconsistent claim: PAID with non-zero patient responsibility
        Claim c7 = new Claim();
        c7.setId(7L);
        c7.setStatus(ClaimStatus.PAID);
        c7.setTotalCharge(new BigDecimal("120.00"));
        c7.setAllowedAmount(new BigDecimal("100.00"));
        c7.setPaidAmount(new BigDecimal("70.00"));
        c7.setPatientResponsibility(new BigDecimal("30.00"));
        c7.setContractualAdjustment(BigDecimal.ZERO);

        when(claimRepository.findAll()).thenReturn(List.of(c1, c2, c3, c4, c5, c6, c7));

        // Ledger totals
        when(ledgerRepository.sumAmountByEntryType(PaymentLedgerEntryType.LEGACY_INITIAL_BALANCE))
                .thenReturn(new BigDecimal("70.00"));
        when(ledgerRepository.sumAmountByEntryType(PaymentLedgerEntryType.PAYER_PAYMENT))
                .thenReturn(new BigDecimal("380.00"));
        when(ledgerRepository.sumAmountByEntryType(PaymentLedgerEntryType.PAYER_REVERSAL))
                .thenReturn(BigDecimal.ZERO);
        when(ledgerRepository.sumAmountByEntryType(PaymentLedgerEntryType.CONTRACTUAL_WRITE_OFF))
                .thenReturn(new BigDecimal("70.00"));
        when(ledgerRepository.sumAmountByEntryType(PaymentLedgerEntryType.CONTRACTUAL_ADJUSTMENT_REVERSAL))
                .thenReturn(BigDecimal.ZERO);

        AccountsReceivableSummaryResponse summary = arService.getArSummary();

        // Total charges: 100 + 500 + 600 + 400 + 200 + 150 + 120 = 2070.00
        assertThat(summary.grossBilledCharges()).isEqualByComparingTo("2070.00");
        assertThat(summary.legacyOpeningPayments()).isEqualByComparingTo("70.00");
        assertThat(summary.postedPayerPayments()).isEqualByComparingTo("380.00");
        assertThat(summary.payerPaymentReversals()).isEqualByComparingTo("0.00");
        assertThat(summary.netRecordedPayerPayments()).isEqualByComparingTo("450.00"); // 70 + 380 = 450
        assertThat(summary.verifiedContractualWriteOffs()).isEqualByComparingTo("70.00"); // 20 + 50 = 70

        // Exposure & AR
        assertThat(summary.patientAr()).isEqualByComparingTo("130.00"); // 100 + 30 = 130
        assertThat(summary.adjudicatedPayerAr()).isEqualByComparingTo("50.00"); // c2 payer obligation
        assertThat(summary.totalCollectibleAr()).isEqualByComparingTo("180.00"); // 130 + 50 = 180
        assertThat(summary.disputedAr()).isEqualByComparingTo("400.00"); // c4 DENIED
        assertThat(summary.unadjudicatedSubmittedExposure()).isEqualByComparingTo("600.00"); // c3 SUBMITTED
        assertThat(summary.rejectedClaimExposure()).isEqualByComparingTo("200.00"); // c5 REJECTED
        assertThat(summary.draftClaimExposure()).isEqualByComparingTo("150.00"); // c6 DRAFT

        // Legacy inconsistency: c7 has 30.00 patient resp on PAID claim, plus discount variance (120 - 100 = 20)
        assertThat(summary.legacyUnclassifiedBalances()).isEqualByComparingTo("50.00"); // 30 + 20 = 50.00

        // Total Outstanding = 2070 - 450 (net payments) - 70 (write-offs) = 1550.00
        assertThat(summary.totalOutstandingExposure()).isEqualByComparingTo("1550.00");
        assertThat(summary.isBalanced()).isTrue();
        assertThat(summary.balanceDiscrepancy()).isEqualByComparingTo("0.00");
        assertThat(summary.totalClaimsCount()).isEqualTo(7);
    }

    @Test
    @DisplayName("Claim AR Breakdown returns accurate metrics and detects legacy discrepancy notes")
    void testClaimArBreakdown() {
        Claim claim = new Claim();
        claim.setId(1L);
        claim.setClaimNumber("CLM-2026-000001");
        claim.setStatus(ClaimStatus.PAID);
        claim.setTotalCharge(new BigDecimal("100.00"));
        claim.setAllowedAmount(new BigDecimal("80.00"));
        claim.setPaidAmount(new BigDecimal("40.00"));
        claim.setPatientResponsibility(new BigDecimal("40.00"));
        claim.setContractualAdjustment(BigDecimal.ZERO);

        when(claimRepository.findById(1L)).thenReturn(Optional.of(claim));
        when(ledgerRepository.findByClaimIdOrderByPostedAtAsc(1L)).thenReturn(Collections.emptyList());

        ClaimArBreakdownResponse breakdown = arService.getClaimArBreakdown(1L);

        assertThat(breakdown.claimId()).isEqualTo(1L);
        assertThat(breakdown.claimNumber()).isEqualTo("CLM-2026-000001");
        assertThat(breakdown.status()).isEqualTo(ClaimStatus.PAID);
        assertThat(breakdown.totalCharge()).isEqualByComparingTo("100.00");
        assertThat(breakdown.allowedAmount()).isEqualByComparingTo("80.00");
        assertThat(breakdown.paidAmount()).isEqualByComparingTo("40.00");
        assertThat(breakdown.patientResponsibility()).isEqualByComparingTo("40.00");
        assertThat(breakdown.remainingBalance()).isEqualByComparingTo("60.00"); // 100 - 40 = 60
        assertThat(breakdown.legacyDiscrepancyNotes())
                .contains("Legacy claim has status PAID but uncollected patient responsibility of $40.00");
    }
}

