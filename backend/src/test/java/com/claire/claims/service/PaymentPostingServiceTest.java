package com.claire.claims.service;

import com.claire.claims.common.ApiExceptions.BusinessRuleException;
import com.claire.claims.common.ApiExceptions.ConflictException;
import com.claire.claims.domain.*;
import com.claire.claims.dto.PaymentPostingDtos.*;
import com.claire.claims.repository.*;
import com.claire.claims.security.CurrentUserProvider;
import com.claire.claims.service.remittance.PaymentPostingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentPostingServiceTest {

    @Mock private RemittanceClaimVoucherRepository vouchers;
    @Mock private RemittanceBatchRepository batches;
    @Mock private PaymentLedgerRepository ledger;
    @Mock private ClaimRepository claims;
    @Mock private ClaimStatusHistoryRepository statusHistories;
    @Mock private CurrentUserProvider currentUser;

    private ClaimStatusMachine statusMachine;
    private PaymentPostingService service;
    private Payer testPayer;
    private Claim testClaim;
    private RemittanceBatch testBatch;
    private RemittanceClaimVoucher testVoucher;

    @BeforeEach
    void setUp() {
        statusMachine = new ClaimStatusMachine();
        service = new PaymentPostingService(vouchers, batches, ledger, claims, statusHistories, statusMachine, currentUser);

        testPayer = new Payer();
        testPayer.setId(2L);
        testPayer.setPayerCode("AETNA01");
        testPayer.setName("Aetna Health Plans");

        testClaim = new Claim();
        testClaim.setId(8L);
        testClaim.setClaimNumber("CLM-2026-000008");
        testClaim.setPayer(testPayer);
        testClaim.setStatus(ClaimStatus.SUBMITTED);
        testClaim.setTotalCharge(new BigDecimal("675.00"));
        testClaim.setAllowedAmount(BigDecimal.ZERO);
        testClaim.setPaidAmount(BigDecimal.ZERO);
        testClaim.setPatientResponsibility(BigDecimal.ZERO);
        testClaim.setContractualAdjustment(BigDecimal.ZERO);
        testClaim.setSubmittedAt(OffsetDateTime.now().minusDays(5));

        testBatch = new RemittanceBatch();
        testBatch.setId(100L);
        testBatch.setPayer(testPayer);
        testBatch.setCheckNumber("EFT-2026-88001");
        testBatch.setCheckDate(LocalDate.now().minusDays(1));
        testBatch.setStatus(RemittanceBatchStatus.VALIDATED);

        testVoucher = new RemittanceClaimVoucher();
        testVoucher.setId(200L);
        testVoucher.setRemittance(testBatch);
        testVoucher.setClaim(testClaim);
        testVoucher.setClaimNumber("CLM-2026-000008");
        testVoucher.setPayerIcn("ICN-9901");
        testVoucher.setClpStatusCode("1");
        testVoucher.setBilledAmount(new BigDecimal("675.00"));
        testVoucher.setAllowedAmount(new BigDecimal("575.00"));
        testVoucher.setPaidAmount(new BigDecimal("540.00"));
        testVoucher.setPatientResponsibility(new BigDecimal("35.00"));
        testVoucher.setContractualAdj(new BigDecimal("100.00"));
        testVoucher.setBalanceStatus("BALANCED");
        testVoucher.setPostingStatus("UNPOSTED");

        lenient().when(currentUser.username()).thenReturn("biller_tester");
    }

    @Test
    @DisplayName("Clean partial payer payment with patient responsibility records two-step audit: SUBMITTED -> ACCEPTED -> PARTIALLY_PAID")
    void testPostVoucherPartialWithPatientResponsibility() {
        when(vouchers.findById(200L)).thenReturn(Optional.of(testVoucher));
        when(ledger.existsByVoucherIdAndEntryType(200L, PaymentLedgerEntryType.PAYER_PAYMENT)).thenReturn(false);

        VoucherPostingResponse response = service.postVoucher(200L);

        assertThat(response.voucherId()).isEqualTo(200L);
        assertThat(response.claimId()).isEqualTo(8L);
        assertThat(response.previousStatus()).isEqualTo(ClaimStatus.SUBMITTED);
        assertThat(response.newStatus()).isEqualTo(ClaimStatus.PARTIALLY_PAID);
        assertThat(response.postedAmount()).isEqualByComparingTo("540.00");
        assertThat(response.patientResponsibility()).isEqualByComparingTo("35.00");
        assertThat(response.contractualWriteOff()).isEqualByComparingTo("100.00");
        assertThat(response.cumulativeClaimPaid()).isEqualByComparingTo("540.00");
        assertThat(response.isFutureDated()).isFalse();
        assertThat(response.settlementNote()).isNull();

        assertThat(testClaim.getStatus()).isEqualTo(ClaimStatus.PARTIALLY_PAID);
        assertThat(testClaim.getPaidAmount()).isEqualByComparingTo("540.00");
        assertThat(testClaim.getAllowedAmount()).isEqualByComparingTo("575.00");
        assertThat(testClaim.getPatientResponsibility()).isEqualByComparingTo("35.00");
        assertThat(testClaim.getContractualAdjustment()).isEqualByComparingTo("100.00");
        assertThat(testVoucher.getPostingStatus()).isEqualTo("POSTED");

        // Verify two discrete transitions were saved: SUBMITTED -> ACCEPTED and ACCEPTED -> PARTIALLY_PAID
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ClaimStatusHistory>> historyCaptor = ArgumentCaptor.forClass(List.class);
        verify(statusHistories).saveAll(historyCaptor.capture());
        List<ClaimStatusHistory> savedHistories = historyCaptor.getValue();
        assertThat(savedHistories).hasSize(2);

        // Step 1: SUBMITTED -> ACCEPTED (Inferred from ERA 835)
        assertThat(savedHistories.get(0).getFromStatus()).isEqualTo(ClaimStatus.SUBMITTED);
        assertThat(savedHistories.get(0).getToStatus()).isEqualTo(ClaimStatus.ACCEPTED);
        assertThat(savedHistories.get(0).getReason()).contains("Payer adjudication inferred via ERA 835 remittance check EFT-2026-88001");
        assertThat(savedHistories.get(0).getReason()).contains("inferred from ERA, not separate 277CA acknowledgment");

        // Step 2: ACCEPTED -> PARTIALLY_PAID
        assertThat(savedHistories.get(1).getFromStatus()).isEqualTo(ClaimStatus.ACCEPTED);
        assertThat(savedHistories.get(1).getToStatus()).isEqualTo(ClaimStatus.PARTIALLY_PAID);
        assertThat(savedHistories.get(1).getReason()).contains("Payment of $540.00 posted from ERA 835 check EFT-2026-88001");

        // Verify ledger entries: 1 for payment, 1 for contractual write-off
        ArgumentCaptor<PaymentLedgerEntry> captor = ArgumentCaptor.forClass(PaymentLedgerEntry.class);
        verify(ledger, times(2)).save(captor.capture());
        List<PaymentLedgerEntry> savedEntries = captor.getAllValues();
        assertThat(savedEntries).hasSize(2);
        assertThat(savedEntries.get(0).getEntryType()).isEqualTo(PaymentLedgerEntryType.PAYER_PAYMENT);
        assertThat(savedEntries.get(0).getAmount()).isEqualByComparingTo("540.00");
        assertThat(savedEntries.get(1).getEntryType()).isEqualTo(PaymentLedgerEntryType.CONTRACTUAL_WRITE_OFF);
        assertThat(savedEntries.get(1).getAmount()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("Clean full payer payment with zero patient responsibility transitions to PAID via two-step audit")
    void testPostVoucherFullPaymentWithoutPatientResponsibility() {
        testVoucher.setPaidAmount(new BigDecimal("575.00"));
        testVoucher.setPatientResponsibility(BigDecimal.ZERO);
        testVoucher.setContractualAdj(new BigDecimal("100.00"));

        when(vouchers.findById(200L)).thenReturn(Optional.of(testVoucher));
        when(ledger.existsByVoucherIdAndEntryType(200L, PaymentLedgerEntryType.PAYER_PAYMENT)).thenReturn(false);

        VoucherPostingResponse response = service.postVoucher(200L);

        assertThat(response.previousStatus()).isEqualTo(ClaimStatus.SUBMITTED);
        assertThat(response.newStatus()).isEqualTo(ClaimStatus.PAID);
        assertThat(response.postedAmount()).isEqualByComparingTo("575.00");
        assertThat(response.patientResponsibility()).isEqualByComparingTo("0.00");
        assertThat(testClaim.getStatus()).isEqualTo(ClaimStatus.PAID);
        assertThat(testClaim.getPaidAmount()).isEqualByComparingTo("575.00");
        assertThat(testClaim.getContractualAdjustment()).isEqualByComparingTo("100.00");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ClaimStatusHistory>> historyCaptor = ArgumentCaptor.forClass(List.class);
        verify(statusHistories).saveAll(historyCaptor.capture());
        List<ClaimStatusHistory> savedHistories = historyCaptor.getValue();
        assertThat(savedHistories).hasSize(2);
        assertThat(savedHistories.get(0).getFromStatus()).isEqualTo(ClaimStatus.SUBMITTED);
        assertThat(savedHistories.get(0).getToStatus()).isEqualTo(ClaimStatus.ACCEPTED);
        assertThat(savedHistories.get(1).getFromStatus()).isEqualTo(ClaimStatus.ACCEPTED);
        assertThat(savedHistories.get(1).getToStatus()).isEqualTo(ClaimStatus.PAID);
    }

    @Test
    @DisplayName("Posting payment to an already PARTIALLY_PAID claim updates balance and prevents no-op transition history")
    void testPreserveLegacyPaymentsAndAccumulate() {
        // Claim 3 had legacy $400.00 paid and was already PARTIALLY_PAID
        testClaim.setPaidAmount(new BigDecimal("400.00"));
        testClaim.setStatus(ClaimStatus.PARTIALLY_PAID);

        testVoucher.setPaidAmount(new BigDecimal("100.00"));
        testVoucher.setPatientResponsibility(new BigDecimal("112.00"));
        testVoucher.setContractualAdj(new BigDecimal("50.00"));

        when(vouchers.findById(200L)).thenReturn(Optional.of(testVoucher));
        when(ledger.existsByVoucherIdAndEntryType(200L, PaymentLedgerEntryType.PAYER_PAYMENT)).thenReturn(false);

        VoucherPostingResponse response = service.postVoucher(200L);

        // 400.00 + 100.00 = 500.00 cumulative paid
        assertThat(response.cumulativeClaimPaid()).isEqualByComparingTo("500.00");
        assertThat(testClaim.getPaidAmount()).isEqualByComparingTo("500.00");
        assertThat(response.postedAmount()).isEqualByComparingTo("100.00");
        assertThat(testClaim.getStatus()).isEqualTo(ClaimStatus.PARTIALLY_PAID);

        // Crucial: No-op PARTIALLY_PAID -> PARTIALLY_PAID transition was NOT saved
        verify(statusHistories, never()).saveAll(any());
    }

    @Test
    @DisplayName("Future-dated remittance check flags isFutureDated and appends settlement warning")
    void testFutureDatedRemittanceWarning() {
        testBatch.setCheckDate(LocalDate.now().plusDays(5));

        when(vouchers.findById(200L)).thenReturn(Optional.of(testVoucher));
        when(ledger.existsByVoucherIdAndEntryType(200L, PaymentLedgerEntryType.PAYER_PAYMENT)).thenReturn(false);

        VoucherPostingResponse response = service.postVoucher(200L);

        assertThat(response.isFutureDated()).isTrue();
        assertThat(response.settlementNote()).isEqualTo("Pending bank cash settlement");

        ArgumentCaptor<PaymentLedgerEntry> captor = ArgumentCaptor.forClass(PaymentLedgerEntry.class);
        verify(ledger, times(2)).save(captor.capture());
        PaymentLedgerEntry paymentEntry = captor.getAllValues().get(0);
        assertThat(paymentEntry.getNotes()).contains("Pending bank cash settlement");
        assertThat(paymentEntry.getCheckDate()).isEqualTo(testBatch.getCheckDate());
    }

    @Test
    @DisplayName("Reject posting an already POSTED voucher with ConflictException")
    void testRejectAlreadyPostedVoucher() {
        testVoucher.setPostingStatus("POSTED");
        when(vouchers.findById(200L)).thenReturn(Optional.of(testVoucher));

        assertThatThrownBy(() -> service.postVoucher(200L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already POSTED");
    }

    @Test
    @DisplayName("Reject posting when voucher balance status is OUT_OF_BALANCE")
    void testRejectUnbalancedVoucher() {
        testVoucher.setBalanceStatus("OUT_OF_BALANCE");
        when(vouchers.findById(200L)).thenReturn(Optional.of(testVoucher));

        assertThatThrownBy(() -> service.postVoucher(200L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Only BALANCED vouchers can be posted");
    }

    @Test
    @DisplayName("Reject posting when claim status is incompatible (DRAFT or VOID)")
    void testRejectIncompatibleClaimStatus() {
        testClaim.setStatus(ClaimStatus.VOID);
        when(vouchers.findById(200L)).thenReturn(Optional.of(testVoucher));

        assertThatThrownBy(() -> service.postVoucher(200L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("incompatible status VOID");
    }

    @Test
    @DisplayName("Reject posting when voucher payer does not match claim payer")
    void testRejectPayerMismatch() {
        Payer differentPayer = new Payer();
        differentPayer.setId(99L);
        differentPayer.setName("Other Payer");
        testBatch.setPayer(differentPayer);

        when(vouchers.findById(200L)).thenReturn(Optional.of(testVoucher));

        assertThatThrownBy(() -> service.postVoucher(200L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("does not match claim payer");
    }

    @Test
    @DisplayName("Reject zero-payment or denial-only voucher from automatic payment posting")
    void testRejectZeroPaymentVoucher() {
        testVoucher.setPaidAmount(BigDecimal.ZERO);
        when(vouchers.findById(200L)).thenReturn(Optional.of(testVoucher));

        assertThatThrownBy(() -> service.postVoucher(200L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Zero-payment or denial-only vouchers cannot be posted");
    }

    @Test
    @DisplayName("Atomically rolls back batch posting if any single voucher is invalid")
    void testBatchPostingAtomicity() {
        RemittanceClaimVoucher invalidVoucher = new RemittanceClaimVoucher();
        invalidVoucher.setId(201L);
        invalidVoucher.setRemittance(testBatch);
        invalidVoucher.setClaim(null); // Unmatched!
        invalidVoucher.setClaimNumber("CLM-UNKNOWN");
        invalidVoucher.setBalanceStatus("BALANCED");
        invalidVoucher.setPostingStatus("UNPOSTED");

        when(batches.findById(100L)).thenReturn(Optional.of(testBatch));
        when(vouchers.findByRemittanceIdOrderBySequenceNoAsc(100L)).thenReturn(List.of(testVoucher, invalidVoucher));

        assertThatThrownBy(() -> service.postBatch(100L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("no matched claim is linked");

        // Verify zero mutations committed
        verify(claims, never()).save(any());
        verify(ledger, never()).save(any());
        verify(batches, never()).save(any());
    }

    @Test
    @DisplayName("Preserves original claim submittedAt timestamp through payment posting")
    void testPreserveSubmittedAtTimestamp() {
        OffsetDateTime originalSubmission = testClaim.getSubmittedAt();
        when(vouchers.findById(200L)).thenReturn(Optional.of(testVoucher));
        when(ledger.existsByVoucherIdAndEntryType(200L, PaymentLedgerEntryType.PAYER_PAYMENT)).thenReturn(false);

        service.postVoucher(200L);

        assertThat(testClaim.getSubmittedAt()).isEqualTo(originalSubmission);
    }
}
