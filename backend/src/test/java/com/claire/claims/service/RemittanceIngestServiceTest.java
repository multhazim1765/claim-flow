package com.claire.claims.service;

import com.claire.claims.common.ApiExceptions.BusinessRuleException;
import com.claire.claims.common.ApiExceptions.ConflictException;
import com.claire.claims.domain.*;
import com.claire.claims.dto.RemittanceDtos.*;
import com.claire.claims.repository.*;
import com.claire.claims.security.CurrentUserProvider;
import com.claire.claims.service.remittance.RemittanceIngestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RemittanceIngestServiceTest {

    @Mock private RemittanceBatchRepository batches;
    @Mock private RemittanceClaimVoucherRepository vouchers;
    @Mock private RemittanceClaimExceptionRepository exceptions;
    @Mock private PayerRepository payers;
    @Mock private ClaimRepository claims;
    @Mock private CurrentUserProvider currentUser;

    private RemittanceIngestService service;
    private Payer testPayer;
    private Claim testClaim;

    @BeforeEach
    void setUp() {
        service = new RemittanceIngestService(batches, vouchers, exceptions, payers, claims, currentUser);

        testPayer = new Payer();
        testPayer.setId(1L);
        testPayer.setPayerCode("AETNA");
        testPayer.setName("Aetna Health Plans");

        testClaim = new Claim();
        testClaim.setId(10L);
        testClaim.setClaimNumber("CLM-2026-000010");
        testClaim.setPayer(testPayer);
        testClaim.setStatus(ClaimStatus.ACCEPTED);
        testClaim.setTotalCharge(new BigDecimal("1000.00"));
        testClaim.setAllowedAmount(BigDecimal.ZERO);
        testClaim.setPaidAmount(BigDecimal.ZERO);
        testClaim.setPatientResponsibility(BigDecimal.ZERO);

        lenient().when(currentUser.username()).thenReturn("biller_test");
    }

    @Test
    @DisplayName("Successfully ingest balanced synthetic remittance batch")
    void testIngestBalancedBatch() {
        when(payers.findById(1L)).thenReturn(Optional.of(testPayer));
        when(batches.existsByPayerIdAndCheckNumberAndCheckDate(any(), any(), any())).thenReturn(false);
        when(claims.findByClaimNumber("CLM-2026-000010")).thenReturn(Optional.of(testClaim));
        when(batches.save(any(RemittanceBatch.class))).thenAnswer(inv -> {
            RemittanceBatch b = inv.getArgument(0);
            b.setId(100L);
            return b;
        });

        var adjInput = new RemittanceAdjustmentInput(AdjustmentGroupCode.CO, "45", null, new BigDecimal("200.00"));
        var claimInput = new RemittanceClaimInput(
                "CLM-2026-000010",
                "ICN-1234",
                "1",
                new BigDecimal("1000.00"),
                new BigDecimal("800.00"),
                new BigDecimal("800.00"),
                BigDecimal.ZERO,
                "ORIGINAL",
                null,
                List.of(adjInput)
        );

        var req = new ImportRemittanceRequest(
                1L, null, "EFT-8899", LocalDate.now(), "ACH", new BigDecimal("800.00"), List.of(claimInput)
        );

        RemittanceBatchDetailResponse resp = service.ingest(req, "test_payload");

        assertThat(resp).isNotNull();
        assertThat(resp.status()).isEqualTo(RemittanceBatchStatus.VALIDATED);
        assertThat(resp.totalPaid()).isEqualByComparingTo("800.00");
        assertThat(resp.claimsCount()).isEqualTo(1);
        assertThat(resp.matchedCount()).isEqualTo(1);
        assertThat(resp.exceptionCount()).isEqualTo(0);

        // STRICT PHASE A GUARANTEE: Claim financial fields are completely untouched!
        assertThat(testClaim.getPaidAmount()).isEqualByComparingTo("0.00");
        assertThat(testClaim.getAllowedAmount()).isEqualByComparingTo("0.00");
        assertThat(testClaim.getPatientResponsibility()).isEqualByComparingTo("0.00");
        assertThat(testClaim.getStatus()).isEqualTo(ClaimStatus.ACCEPTED);
    }

    @Test
    @DisplayName("Reject duplicate remittance import by payer and check number")
    void testRejectDuplicateBatch() {
        when(payers.findById(1L)).thenReturn(Optional.of(testPayer));
        when(batches.existsByPayerIdAndCheckNumberAndCheckDate(1L, "EFT-DUP", LocalDate.of(2026, 11, 20))).thenReturn(true);

        var req = new ImportRemittanceRequest(
                1L, null, "EFT-DUP", LocalDate.of(2026, 11, 20), "ACH", new BigDecimal("500.00"), List.of()
        );

        assertThatThrownBy(() -> service.ingest(req, null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("was already imported");
    }

    @Test
    @DisplayName("Reject voucher duplicate within the same batch even when ICN is missing")
    void testVoucherDuplicatePreventionWithoutIcn() {
        when(payers.findById(1L)).thenReturn(Optional.of(testPayer));
        when(batches.existsByPayerIdAndCheckNumberAndCheckDate(any(), any(), any())).thenReturn(false);

        var c1 = new RemittanceClaimInput("CLM-100", null, "1", new BigDecimal("500.00"), new BigDecimal("400.00"), new BigDecimal("400.00"), BigDecimal.ZERO, "ORIGINAL", null, null);
        var c2 = new RemittanceClaimInput("CLM-100", null, "1", new BigDecimal("500.00"), new BigDecimal("400.00"), new BigDecimal("400.00"), BigDecimal.ZERO, "ORIGINAL", null, null);

        var req = new ImportRemittanceRequest(
                1L, null, "CHK-100", LocalDate.now(), "CHK", new BigDecimal("800.00"), List.of(c1, c2)
        );

        assertThatThrownBy(() -> service.ingest(req, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Duplicate claim voucher for claim number 'CLM-100'");
    }

    @Test
    @DisplayName("Unmatched claim routes to exception queue without failing batch")
    void testUnmatchedClaimRoutesToException() {
        when(payers.findById(1L)).thenReturn(Optional.of(testPayer));
        when(batches.existsByPayerIdAndCheckNumberAndCheckDate(any(), any(), any())).thenReturn(false);
        when(claims.findByClaimNumber("CLM-UNKNOWN")).thenReturn(Optional.empty());
        when(batches.save(any(RemittanceBatch.class))).thenAnswer(inv -> {
            RemittanceBatch b = inv.getArgument(0);
            b.setId(101L);
            return b;
        });

        var claimInput = new RemittanceClaimInput(
                "CLM-UNKNOWN", "ICN-999", "1", new BigDecimal("300.00"), new BigDecimal("300.00"), new BigDecimal("300.00"), BigDecimal.ZERO, "ORIGINAL", null, null
        );
        var req = new ImportRemittanceRequest(
                1L, null, "CHK-99", LocalDate.now(), "CHK", new BigDecimal("300.00"), List.of(claimInput)
        );

        RemittanceBatchDetailResponse resp = service.ingest(req, null);

        assertThat(resp.matchedCount()).isEqualTo(0);
        assertThat(resp.exceptionCount()).isEqualTo(1);
        verify(batches).save(argThat(b -> b.getExceptions().size() == 1 && b.getExceptions().get(0).getExceptionType().equals("UNMATCHED_CLAIM")));
    }

    @Test
    @DisplayName("Out-of-balance voucher flags batch as OUT_OF_BALANCE")
    void testOutOfBalanceVoucher() {
        when(payers.findById(1L)).thenReturn(Optional.of(testPayer));
        when(batches.existsByPayerIdAndCheckNumberAndCheckDate(any(), any(), any())).thenReturn(false);
        when(claims.findByClaimNumber("CLM-2026-000010")).thenReturn(Optional.of(testClaim));
        when(batches.save(any(RemittanceBatch.class))).thenAnswer(inv -> {
            RemittanceBatch b = inv.getArgument(0);
            b.setId(102L);
            return b;
        });

        // Billed 1000.00, Paid 600.00, CO-45 200.00 -> Sum = 800.00 != 1000.00
        var adjInput = new RemittanceAdjustmentInput(AdjustmentGroupCode.CO, "45", null, new BigDecimal("200.00"));
        var claimInput = new RemittanceClaimInput(
                "CLM-2026-000010", "ICN-55", "1", new BigDecimal("1000.00"), new BigDecimal("800.00"), new BigDecimal("600.00"), BigDecimal.ZERO, "ORIGINAL", null, List.of(adjInput)
        );
        var req = new ImportRemittanceRequest(
                1L, null, "EFT-77", LocalDate.now(), "ACH", new BigDecimal("600.00"), List.of(claimInput)
        );

        RemittanceBatchDetailResponse resp = service.ingest(req, null);

        assertThat(resp.status()).isEqualTo(RemittanceBatchStatus.OUT_OF_BALANCE);
        assertThat(resp.exceptionCount()).isGreaterThanOrEqualTo(1);
    }
}

