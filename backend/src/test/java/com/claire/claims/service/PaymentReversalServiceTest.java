package com.claire.claims.service;

import com.claire.claims.common.ApiExceptions.BusinessRuleException;
import com.claire.claims.common.ApiExceptions.ConflictException;
import com.claire.claims.common.ApiExceptions.NotFoundException;
import com.claire.claims.domain.*;
import com.claire.claims.dto.PaymentReversalDtos.PaymentReversalRequest;
import com.claire.claims.dto.PaymentReversalDtos.PaymentReversalResponse;
import com.claire.claims.repository.ClaimRepository;
import com.claire.claims.repository.ClaimStatusHistoryRepository;
import com.claire.claims.repository.PaymentLedgerRepository;
import com.claire.claims.security.CurrentUserProvider;
import com.claire.claims.service.remittance.PaymentReversalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentReversalServiceTest {

    @Mock
    private ClaimRepository claimRepository;

    @Mock
    private PaymentLedgerRepository ledgerRepository;

    @Mock
    private ClaimStatusHistoryRepository statusHistoryRepository;

    @Mock
    private ClaimStatusMachine statusMachine;

    @Mock
    private CurrentUserProvider currentUserProvider;

    private PaymentReversalService reversalService;
    private Clock fixedClock;

    private Claim claim;
    private RemittanceClaimVoucher voucher;
    private PaymentLedgerEntry paymentEntry;
    private PaymentLedgerEntry writeOffEntry;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(Instant.parse("2026-11-20T10:00:00Z"), ZoneId.of("UTC"));
        reversalService = new PaymentReversalService(
                claimRepository,
                ledgerRepository,
                statusHistoryRepository,
                statusMachine,
                currentUserProvider,
                fixedClock
        );

        lenient().when(currentUserProvider.username()).thenReturn("biller_agent");

        claim = new Claim();
        claim.setId(10L);
        claim.setClaimNumber("CLM-2026-000010");
        claim.setStatus(ClaimStatus.PARTIALLY_PAID);
        claim.setTotalCharge(new BigDecimal("500.00"));
        claim.setAllowedAmount(new BigDecimal("450.00"));
        claim.setPaidAmount(new BigDecimal("350.00"));
        claim.setPatientResponsibility(new BigDecimal("100.00"));
        claim.setContractualAdjustment(new BigDecimal("50.00"));

        voucher = new RemittanceClaimVoucher();
        voucher.setId(20L);
        voucher.setPostingStatus("POSTED");

        paymentEntry = new PaymentLedgerEntry();
        paymentEntry.setId(100L);
        paymentEntry.setClaim(claim);
        paymentEntry.setVoucher(voucher);
        paymentEntry.setEntryType(PaymentLedgerEntryType.PAYER_PAYMENT);
        paymentEntry.setAmount(new BigDecimal("350.00"));
        paymentEntry.setCheckNumber("CHK-9999");
        paymentEntry.setCheckDate(LocalDate.of(2026, 11, 15));

        writeOffEntry = new PaymentLedgerEntry();
        writeOffEntry.setId(101L);
        writeOffEntry.setClaim(claim);
        writeOffEntry.setVoucher(voucher);
        writeOffEntry.setEntryType(PaymentLedgerEntryType.CONTRACTUAL_WRITE_OFF);
        writeOffEntry.setAmount(new BigDecimal("50.00"));
    }

    @Test
    @DisplayName("Successfully reverse payment and contractual adjustment, reopening claim to ACCEPTED")
    void testSuccessfulReversalReopeningToAccepted() {
        PaymentReversalRequest request = new PaymentReversalRequest(
                100L,
                "Overpayment recoupment by payer",
                "REV-CHK-9999",
                LocalDate.of(2026, 11, 20),
                "Payer demand letter #4432"
        );

        when(claimRepository.findById(10L)).thenReturn(Optional.of(claim));
        when(ledgerRepository.findById(100L)).thenReturn(Optional.of(paymentEntry));
        when(ledgerRepository.existsByOriginalLedgerEntryIdAndEntryType(100L, PaymentLedgerEntryType.PAYER_REVERSAL))
                .thenReturn(false);
        when(ledgerRepository.findByVoucherIdAndEntryType(20L, PaymentLedgerEntryType.CONTRACTUAL_WRITE_OFF))
                .thenReturn(Optional.of(writeOffEntry));
        when(ledgerRepository.existsByOriginalLedgerEntryIdAndEntryType(101L, PaymentLedgerEntryType.CONTRACTUAL_ADJUSTMENT_REVERSAL))
                .thenReturn(false);

        when(ledgerRepository.save(any(PaymentLedgerEntry.class))).thenAnswer(inv -> {
            PaymentLedgerEntry e = inv.getArgument(0);
            if (e.getId() == null) {
                e.setId(999L);
            }
            return e;
        });

        ClaimStatusHistory history = new ClaimStatusHistory(claim, ClaimStatus.PARTIALLY_PAID, ClaimStatus.ACCEPTED, "Reversal", "biller_agent");
        when(statusMachine.applyPaymentReversal(eq(claim), eq(new BigDecimal("0.00")), eq("REV-CHK-9999"), eq("biller_agent")))
                .thenAnswer(inv -> {
                    claim.setStatus(ClaimStatus.ACCEPTED);
                    return List.of(history);
                });

        PaymentReversalResponse response = reversalService.reversePayment(10L, request);

        assertThat(response.claimId()).isEqualTo(10L);
        assertThat(response.originalLedgerId()).isEqualTo(100L);
        assertThat(response.reversedPaymentAmount()).isEqualByComparingTo("350.00");
        assertThat(response.reversedContractualAdjustment()).isEqualByComparingTo("50.00");
        assertThat(response.remainingPaidAmount()).isEqualByComparingTo("0.00");
        assertThat(response.remainingContractualAdjustment()).isEqualByComparingTo("0.00");
        assertThat(response.previousClaimStatus()).isEqualTo(ClaimStatus.PARTIALLY_PAID);
        assertThat(response.updatedClaimStatus()).isEqualTo(ClaimStatus.ACCEPTED);
        assertThat(response.reversalReason()).isEqualTo("Overpayment recoupment by payer");
        assertThat(response.postedBy()).isEqualTo("biller_agent");

        // Verify ledger entries persisted
        ArgumentCaptor<PaymentLedgerEntry> captor = ArgumentCaptor.forClass(PaymentLedgerEntry.class);
        verify(ledgerRepository, times(2)).save(captor.capture());
        List<PaymentLedgerEntry> saved = captor.getAllValues();

        PaymentLedgerEntry revPayment = saved.get(0);
        assertThat(revPayment.getEntryType()).isEqualTo(PaymentLedgerEntryType.PAYER_REVERSAL);
        assertThat(revPayment.getAmount()).isEqualByComparingTo("-350.00");
        assertThat(revPayment.getOriginalLedgerEntry()).isEqualTo(paymentEntry);

        PaymentLedgerEntry revCo = saved.get(1);
        assertThat(revCo.getEntryType()).isEqualTo(PaymentLedgerEntryType.CONTRACTUAL_ADJUSTMENT_REVERSAL);
        assertThat(revCo.getAmount()).isEqualByComparingTo("-50.00");
        assertThat(revCo.getOriginalLedgerEntry()).isEqualTo(writeOffEntry);

        // Verify status history persisted
        verify(statusHistoryRepository).saveAll(any());
        // Verify claim balances reset
        assertThat(claim.getAllowedAmount()).isEqualByComparingTo("0.00");
        assertThat(claim.getPaidAmount()).isEqualByComparingTo("0.00");
        assertThat(claim.getPatientResponsibility()).isEqualByComparingTo("0.00");
        assertThat(claim.getContractualAdjustment()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Claim not found throws NotFoundException")
    void testClaimNotFoundThrows() {
        PaymentReversalRequest request = new PaymentReversalRequest(100L, "Reason", null, null, null);
        when(claimRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reversalService.reversePayment(999L, request))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Claim 999 was not found");
    }

    @Test
    @DisplayName("Original ledger entry not found throws NotFoundException")
    void testLedgerNotFoundThrows() {
        PaymentReversalRequest request = new PaymentReversalRequest(999L, "Reason", null, null, null);
        when(claimRepository.findById(10L)).thenReturn(Optional.of(claim));
        when(ledgerRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reversalService.reversePayment(10L, request))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("PaymentLedgerEntry 999 was not found");
    }

    @Test
    @DisplayName("Original ledger belonging to another claim throws BusinessRuleException")
    void testLedgerBelongingToAnotherClaimThrows() {
        Claim otherClaim = new Claim();
        otherClaim.setId(99L);
        paymentEntry.setClaim(otherClaim);

        PaymentReversalRequest request = new PaymentReversalRequest(100L, "Reason", null, null, null);
        when(claimRepository.findById(10L)).thenReturn(Optional.of(claim));
        when(ledgerRepository.findById(100L)).thenReturn(Optional.of(paymentEntry));

        assertThatThrownBy(() -> reversalService.reversePayment(10L, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("does not belong to claim 10");
    }

    @Test
    @DisplayName("Reversing legacy initial balance throws BusinessRuleException")
    void testReversingLegacyInitialBalanceThrows() {
        paymentEntry.setEntryType(PaymentLedgerEntryType.LEGACY_INITIAL_BALANCE);

        PaymentReversalRequest request = new PaymentReversalRequest(100L, "Reason", null, null, null);
        when(claimRepository.findById(10L)).thenReturn(Optional.of(claim));
        when(ledgerRepository.findById(100L)).thenReturn(Optional.of(paymentEntry));

        assertThatThrownBy(() -> reversalService.reversePayment(10L, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Reversal of legacy initial baseline balances is not supported");
    }

    @Test
    @DisplayName("Reversing already reversed payment throws ConflictException")
    void testDuplicateReversalThrowsConflict() {
        PaymentReversalRequest request = new PaymentReversalRequest(100L, "Reason", null, null, null);
        when(claimRepository.findById(10L)).thenReturn(Optional.of(claim));
        when(ledgerRepository.findById(100L)).thenReturn(Optional.of(paymentEntry));
        when(ledgerRepository.existsByOriginalLedgerEntryIdAndEntryType(100L, PaymentLedgerEntryType.PAYER_REVERSAL))
                .thenReturn(true);

        assertThatThrownBy(() -> reversalService.reversePayment(10L, request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("has already been reversed");
    }

    @Test
    @DisplayName("Reversing unposted voucher throws BusinessRuleException")
    void testReversingUnpostedVoucherThrows() {
        voucher.setPostingStatus("UNPOSTED");

        PaymentReversalRequest request = new PaymentReversalRequest(100L, "Reason", null, null, null);
        when(claimRepository.findById(10L)).thenReturn(Optional.of(claim));
        when(ledgerRepository.findById(100L)).thenReturn(Optional.of(paymentEntry));

        assertThatThrownBy(() -> reversalService.reversePayment(10L, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("is not in POSTED status");
    }
}
