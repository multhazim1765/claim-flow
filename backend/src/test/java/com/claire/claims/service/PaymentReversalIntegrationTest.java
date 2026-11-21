package com.claire.claims.service;

import com.claire.claims.domain.*;
import com.claire.claims.dto.PaymentReversalDtos.PaymentReversalRequest;
import com.claire.claims.dto.PaymentReversalDtos.PaymentReversalResponse;
import com.claire.claims.repository.*;
import com.claire.claims.service.remittance.PaymentReversalService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.test.context.support.WithMockUser;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class PaymentReversalIntegrationTest {

    @Autowired
    private PaymentReversalService reversalService;

    @Autowired
    private ClaimRepository claimRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private ProviderRepository providerRepository;

    @Autowired
    private PayerRepository payerRepository;

    @Autowired
    private RemittanceBatchRepository batchRepository;

    @Autowired
    private RemittanceClaimVoucherRepository voucherRepository;

    @Autowired
    private PaymentLedgerRepository ledgerRepository;

    @Autowired
    private ClaimStatusHistoryRepository statusHistoryRepository;

    private Claim testClaim;
    private RemittanceBatch testBatch;
    private RemittanceClaimVoucher testVoucher;
    private PaymentLedgerEntry testPaymentEntry;
    private PaymentLedgerEntry testWriteOffEntry;

    private static final String TEST_CLAIM_NUMBER = "CLM-TEST-REV-9999";
    private static final String TEST_BATCH_CONTROL = "BATCH-REV-9999";

    @BeforeEach
    void setUp() {
        cleanTestData();

        Patient patient = patientRepository.findAll().stream().findFirst().orElseThrow();
        Provider provider = providerRepository.findAll().stream().findFirst().orElseThrow();
        Payer payer = payerRepository.findAll().stream().findFirst().orElseThrow();

        // 1. Create Synthetic Claim in PAID status
        testClaim = new Claim();
        testClaim.setClaimNumber(TEST_CLAIM_NUMBER);
        testClaim.setPatient(patient);
        testClaim.setProvider(provider);
        testClaim.setPayer(payer);
        testClaim.setStatus(ClaimStatus.PAID);
        testClaim.setServiceDateFrom(LocalDate.of(2026, 10, 15));
        testClaim.setServiceDateTo(LocalDate.of(2026, 10, 15));
        testClaim.setPlaceOfService("11");
        testClaim.setTotalCharge(new BigDecimal("500.00"));
        testClaim.setAllowedAmount(new BigDecimal("450.00"));
        testClaim.setPaidAmount(new BigDecimal("400.00"));
        testClaim.setPatientResponsibility(new BigDecimal("50.00"));
        testClaim.setContractualAdjustment(new BigDecimal("50.00"));
        testClaim.setCreatedBy("biller_integ");
        testClaim.setSubmittedAt(OffsetDateTime.now().minusDays(5));
        testClaim = claimRepository.save(testClaim);

        // 2. Create Remittance Batch
        testBatch = new RemittanceBatch();
        testBatch.setBatchControlNumber(TEST_BATCH_CONTROL);
        testBatch.setPayer(payer);
        testBatch.setCheckNumber("CHK-REV-9999");
        testBatch.setCheckDate(LocalDate.now().minusDays(2));
        testBatch.setTotalPaid(new BigDecimal("400.00"));
        testBatch.setClaimsCount(1);
        testBatch.setMatchedCount(1);
        testBatch.setExceptionCount(0);
        testBatch.setStatus(RemittanceBatchStatus.POSTED);
        testBatch.setPaymentMethod("ACH");
        testBatch.setImportedBy("biller_integ");
        testBatch = batchRepository.save(testBatch);

        // 3. Create Voucher in POSTED status
        testVoucher = new RemittanceClaimVoucher();
        testVoucher.setRemittance(testBatch);
        testVoucher.setClaim(testClaim);
        testVoucher.setSequenceNo(1);
        testVoucher.setClaimNumber(TEST_CLAIM_NUMBER);
        testVoucher.setPayerIcn("ICN-REV-9999");
        testVoucher.setClpStatusCode("1");
        testVoucher.setBilledAmount(new BigDecimal("500.00"));
        testVoucher.setAllowedAmount(new BigDecimal("450.00"));
        testVoucher.setPaidAmount(new BigDecimal("400.00"));
        testVoucher.setPatientResponsibility(new BigDecimal("50.00"));
        testVoucher.setContractualAdj(new BigDecimal("50.00"));
        testVoucher.setBalanceStatus("BALANCED");
        testVoucher.setPostingStatus("POSTED");
        testVoucher.setPostedBy("biller_integ");
        testVoucher.setPostedAt(OffsetDateTime.now().minusDays(1));
        testVoucher = voucherRepository.save(testVoucher);

        // 4. Create posted Payment and Write-Off in Ledger
        testPaymentEntry = new PaymentLedgerEntry();
        testPaymentEntry.setClaim(testClaim);
        testPaymentEntry.setVoucher(testVoucher);
        testPaymentEntry.setRemittance(testBatch);
        testPaymentEntry.setEntryType(PaymentLedgerEntryType.PAYER_PAYMENT);
        testPaymentEntry.setAmount(new BigDecimal("400.00"));
        testPaymentEntry.setPayerIcn("ICN-REV-9999");
        testPaymentEntry.setCheckNumber("CHK-REV-9999");
        testPaymentEntry.setCheckDate(LocalDate.now().minusDays(2));
        testPaymentEntry.setPostedBy("biller_integ");
        testPaymentEntry = ledgerRepository.save(testPaymentEntry);

        testWriteOffEntry = new PaymentLedgerEntry();
        testWriteOffEntry.setClaim(testClaim);
        testWriteOffEntry.setVoucher(testVoucher);
        testWriteOffEntry.setRemittance(testBatch);
        testWriteOffEntry.setEntryType(PaymentLedgerEntryType.CONTRACTUAL_WRITE_OFF);
        testWriteOffEntry.setAmount(new BigDecimal("50.00"));
        testWriteOffEntry.setPayerIcn("ICN-REV-9999");
        testWriteOffEntry.setCheckNumber("CHK-REV-9999");
        testWriteOffEntry.setCheckDate(LocalDate.now().minusDays(2));
        testWriteOffEntry.setPostedBy("biller_integ");
        testWriteOffEntry = ledgerRepository.save(testWriteOffEntry);
    }

    @AfterEach
    void tearDown() {
        cleanTestData();
    }

    private void cleanTestData() {
        claimRepository.findByClaimNumber(TEST_CLAIM_NUMBER).ifPresent(c -> {
            List<RemittanceClaimVoucher> vList = voucherRepository.findByClaimIdOrderByPostedAtDesc(c.getId());
            for (RemittanceClaimVoucher v : vList) {
                ledgerRepository.deleteAll(ledgerRepository.findByVoucherId(v.getId()));
                voucherRepository.delete(v);
            }
            statusHistoryRepository.deleteAll(statusHistoryRepository.findByClaimIdOrderByChangedAtAsc(c.getId()));
            ledgerRepository.deleteAll(ledgerRepository.findByClaimIdOrderByPostedAtAsc(c.getId()));
            claimRepository.delete(c);
        });
        batchRepository.findByBatchControlNumber(TEST_BATCH_CONTROL).ifPresent(batchRepository::delete);
    }

    @Test
    @WithMockUser(username = "biller_integ", roles = {"BILLER"})
    @DisplayName("Integration: Live reversal against PostgreSQL reopens PAID claim to ACCEPTED and creates signed ledger rows")
    void testRealReversalExecutionInPostgres() {
        PaymentReversalRequest request = new PaymentReversalRequest(
                testPaymentEntry.getId(),
                "Authorized recoupment by payer audit",
                "CHK-REC-9999",
                LocalDate.now(),
                "Formal reversal notice #7788"
        );

        PaymentReversalResponse response = reversalService.reversePayment(testClaim.getId(), request);

        assertThat(response.claimId()).isEqualTo(testClaim.getId());
        assertThat(response.previousClaimStatus()).isEqualTo(ClaimStatus.PAID);
        assertThat(response.updatedClaimStatus()).isEqualTo(ClaimStatus.ACCEPTED);
        assertThat(response.reversedPaymentAmount()).isEqualByComparingTo("400.00");
        assertThat(response.reversedContractualAdjustment()).isEqualByComparingTo("50.00");
        assertThat(response.remainingPaidAmount()).isEqualByComparingTo("0.00");
        assertThat(response.remainingContractualAdjustment()).isEqualByComparingTo("0.00");

        // Verify Claim updated in PostgreSQL
        Claim updated = claimRepository.findById(testClaim.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(ClaimStatus.ACCEPTED);
        assertThat(updated.getPaidAmount()).isEqualByComparingTo("0.00");
        assertThat(updated.getAllowedAmount()).isEqualByComparingTo("0.00");
        assertThat(updated.getPatientResponsibility()).isEqualByComparingTo("0.00");
        assertThat(updated.getContractualAdjustment()).isEqualByComparingTo("0.00");

        // Verify ledger entries in PostgreSQL
        List<PaymentLedgerEntry> entries = ledgerRepository.findByClaimIdOrderByPostedAtAsc(testClaim.getId());
        assertThat(entries).hasSize(4); // 2 original + 2 reversals

        PaymentLedgerEntry revPayment = entries.stream()
                .filter(e -> e.getEntryType() == PaymentLedgerEntryType.PAYER_REVERSAL)
                .findFirst().orElseThrow();
        assertThat(revPayment.getAmount()).isEqualByComparingTo("-400.00");
        assertThat(revPayment.getOriginalLedgerEntry().getId()).isEqualTo(testPaymentEntry.getId());

        PaymentLedgerEntry revCo = entries.stream()
                .filter(e -> e.getEntryType() == PaymentLedgerEntryType.CONTRACTUAL_ADJUSTMENT_REVERSAL)
                .findFirst().orElseThrow();
        assertThat(revCo.getAmount()).isEqualByComparingTo("-50.00");
        assertThat(revCo.getOriginalLedgerEntry().getId()).isEqualTo(testWriteOffEntry.getId());

        // Verify status history in PostgreSQL
        List<ClaimStatusHistory> history = statusHistoryRepository.findByClaimIdOrderByChangedAtAsc(testClaim.getId());
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getFromStatus()).isEqualTo(ClaimStatus.PAID);
        assertThat(history.get(0).getToStatus()).isEqualTo(ClaimStatus.ACCEPTED);
    }

    @Test
    @WithMockUser(username = "biller_integ", roles = {"BILLER"})
    @DisplayName("Integration: Duplicate reversal is prevented by database unique index uq_ledger_single_reversal")
    void testPostgresDuplicateReversalConstraintViolation() {
        PaymentReversalRequest request = new PaymentReversalRequest(
                testPaymentEntry.getId(),
                "First reversal",
                "CHK-REC-9999",
                LocalDate.now(),
                null
        );

        reversalService.reversePayment(testClaim.getId(), request);

        // Attempting a second reversal against the same payment entry
        assertThatThrownBy(() -> reversalService.reversePayment(testClaim.getId(), request))
                .hasMessageContaining("has already been reversed");
    }

    @Test
    @DisplayName("Integration: PostgreSQL check constraint ck_ledger_amount_sign rejects positive reversal amount")
    void testPostgresCheckConstraintPositiveReversalThrows() {
        PaymentLedgerEntry badReversal = new PaymentLedgerEntry();
        badReversal.setClaim(testClaim);
        badReversal.setVoucher(testVoucher);
        badReversal.setRemittance(testBatch);
        badReversal.setEntryType(PaymentLedgerEntryType.PAYER_REVERSAL);
        badReversal.setAmount(new BigDecimal("100.00")); // Positive! Must violate ck_ledger_amount_sign
        badReversal.setPayerIcn("ICN-BAD");
        badReversal.setCheckNumber("CHK-BAD");
        badReversal.setPostedBy("biller_integ");

        assertThatThrownBy(() -> {
            ledgerRepository.saveAndFlush(badReversal);
        }).isInstanceOf(DataIntegrityViolationException.class);
    }
}

