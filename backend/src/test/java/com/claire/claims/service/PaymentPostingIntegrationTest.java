package com.claire.claims.service;

import com.claire.claims.domain.*;
import com.claire.claims.dto.PaymentPostingDtos.VoucherPostingResponse;
import com.claire.claims.repository.*;
import com.claire.claims.service.remittance.PaymentPostingService;
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
class PaymentPostingIntegrationTest {

    @Autowired
    private PaymentPostingService postingService;

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

    private static final String TEST_CLAIM_NUMBER = "CLM-TEST-IT-8888";
    private static final String TEST_BATCH_CONTROL = "BATCH-IT-8888";

    @BeforeEach
    void setUp() {
        cleanTestData();

        Patient patient = patientRepository.findAll().stream().findFirst().orElseThrow();
        Provider provider = providerRepository.findAll().stream().findFirst().orElseThrow();
        Payer payer = payerRepository.findAll().stream().findFirst().orElseThrow();

        // 1. Create Synthetic Claim in SUBMITTED status
        testClaim = new Claim();
        testClaim.setClaimNumber(TEST_CLAIM_NUMBER);
        testClaim.setPatient(patient);
        testClaim.setProvider(provider);
        testClaim.setPayer(payer);
        testClaim.setStatus(ClaimStatus.SUBMITTED);
        testClaim.setServiceDateFrom(LocalDate.of(2026, 10, 1));
        testClaim.setServiceDateTo(LocalDate.of(2026, 10, 1));
        testClaim.setPlaceOfService("11");
        testClaim.setTotalCharge(new BigDecimal("500.00"));
        testClaim.setAllowedAmount(BigDecimal.ZERO);
        testClaim.setPaidAmount(BigDecimal.ZERO);
        testClaim.setPatientResponsibility(BigDecimal.ZERO);
        testClaim.setContractualAdjustment(BigDecimal.ZERO);
        testClaim.setCreatedBy("biller_integ");
        testClaim.setSubmittedAt(OffsetDateTime.now().minusDays(3));
        testClaim = claimRepository.save(testClaim);

        // 2. Create Remittance Batch
        testBatch = new RemittanceBatch();
        testBatch.setBatchControlNumber(TEST_BATCH_CONTROL);
        testBatch.setPayer(payer);
        testBatch.setCheckNumber("CHK-IT-8888");
        testBatch.setCheckDate(LocalDate.now().minusDays(1));
        testBatch.setTotalPaid(new BigDecimal("420.00"));
        testBatch.setClaimsCount(1);
        testBatch.setMatchedCount(1);
        testBatch.setExceptionCount(0);
        testBatch.setStatus(RemittanceBatchStatus.VALIDATED);
        testBatch.setPaymentMethod("ACH");
        testBatch.setImportedBy("biller_integ");
        testBatch = batchRepository.save(testBatch);

        // 3. Create Remittance Claim Voucher
        testVoucher = new RemittanceClaimVoucher();
        testVoucher.setRemittance(testBatch);
        testVoucher.setClaim(testClaim);
        testVoucher.setClaimNumber(TEST_CLAIM_NUMBER);
        testVoucher.setSequenceNo(1);
        testVoucher.setPayerIcn("ICN-IT-8888");
        testVoucher.setClpStatusCode("1");
        testVoucher.setBilledAmount(new BigDecimal("500.00"));
        testVoucher.setAllowedAmount(new BigDecimal("450.00"));
        testVoucher.setPaidAmount(new BigDecimal("420.00"));
        testVoucher.setPatientResponsibility(new BigDecimal("30.00"));
        testVoucher.setContractualAdj(new BigDecimal("50.00"));
        testVoucher.setBalanceStatus("BALANCED");
        testVoucher.setPostingStatus("UNPOSTED");
        testVoucher = voucherRepository.save(testVoucher);
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
    @DisplayName("Integration: Real posting from SUBMITTED persists two discrete audit transitions in PostgreSQL")
    void testRealTwoStepAuditHistoryInDatabase() {
        VoucherPostingResponse response = postingService.postVoucher(testVoucher.getId());

        assertThat(response.voucherId()).isEqualTo(testVoucher.getId());
        assertThat(response.previousStatus()).isEqualTo(ClaimStatus.SUBMITTED);
        assertThat(response.newStatus()).isEqualTo(ClaimStatus.PARTIALLY_PAID);
        assertThat(response.postedAmount()).isEqualByComparingTo("420.00");
        assertThat(response.patientResponsibility()).isEqualByComparingTo("30.00");
        assertThat(response.contractualWriteOff()).isEqualByComparingTo("50.00");
        assertThat(response.isFutureDated()).isFalse();
        assertThat(response.settlementNote()).isNull();

        // Verify Claim updated in PostgreSQL
        Claim updated = claimRepository.findById(testClaim.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(ClaimStatus.PARTIALLY_PAID);
        assertThat(updated.getPaidAmount()).isEqualByComparingTo("420.00");
        assertThat(updated.getAllowedAmount()).isEqualByComparingTo("450.00");
        assertThat(updated.getPatientResponsibility()).isEqualByComparingTo("30.00");
        assertThat(updated.getContractualAdjustment()).isEqualByComparingTo("50.00");

        // Verify exact two status history rows in PostgreSQL
        List<ClaimStatusHistory> histories = statusHistoryRepository.findByClaimIdOrderByChangedAtAsc(testClaim.getId());
        assertThat(histories).hasSize(2);

        // Step 1: SUBMITTED -> ACCEPTED
        assertThat(histories.get(0).getFromStatus()).isEqualTo(ClaimStatus.SUBMITTED);
        assertThat(histories.get(0).getToStatus()).isEqualTo(ClaimStatus.ACCEPTED);
        assertThat(histories.get(0).getReason()).contains("Payer adjudication inferred via ERA 835 remittance check CHK-IT-8888");
        assertThat(histories.get(0).getReason()).contains("inferred from ERA, not separate 277CA acknowledgment");
        assertThat(histories.get(0).getChangedBy()).isEqualTo("biller_integ");

        // Step 2: ACCEPTED -> PARTIALLY_PAID
        assertThat(histories.get(1).getFromStatus()).isEqualTo(ClaimStatus.ACCEPTED);
        assertThat(histories.get(1).getToStatus()).isEqualTo(ClaimStatus.PARTIALLY_PAID);
        assertThat(histories.get(1).getReason()).contains("Payment of $420.00 posted from ERA 835 check CHK-IT-8888");
        assertThat(histories.get(1).getChangedBy()).isEqualTo("biller_integ");

        // Verify ledger entries in PostgreSQL
        List<PaymentLedgerEntry> ledgerEntries = ledgerRepository.findByClaimIdOrderByPostedAtAsc(testClaim.getId());
        assertThat(ledgerEntries).hasSize(2);
        assertThat(ledgerEntries.get(0).getEntryType()).isEqualTo(PaymentLedgerEntryType.PAYER_PAYMENT);
        assertThat(ledgerEntries.get(0).getAmount()).isEqualByComparingTo("420.00");
        assertThat(ledgerEntries.get(1).getEntryType()).isEqualTo(PaymentLedgerEntryType.CONTRACTUAL_WRITE_OFF);
        assertThat(ledgerEntries.get(1).getAmount()).isEqualByComparingTo("50.00");
    }

    @Test
    @WithMockUser(username = "biller_integ", roles = {"BILLER"})
    @DisplayName("Integration: Future-dated check date correctly sets isFutureDated and settlement note in DB")
    void testFutureDatedRemittanceIntegration() {
        testBatch.setCheckDate(LocalDate.now().plusDays(3));
        batchRepository.save(testBatch);

        VoucherPostingResponse response = postingService.postVoucher(testVoucher.getId());

        assertThat(response.isFutureDated()).isTrue();
        assertThat(response.settlementNote()).isEqualTo("Pending bank cash settlement");

        List<PaymentLedgerEntry> ledgerEntries = ledgerRepository.findByClaimIdOrderByPostedAtAsc(testClaim.getId());
        assertThat(ledgerEntries).hasSize(2);
        assertThat(ledgerEntries.get(0).getNotes()).contains("Pending bank cash settlement");
        assertThat(ledgerEntries.get(1).getNotes()).contains("Pending bank cash settlement");
    }

    @Test
    @WithMockUser(username = "biller_integ", roles = {"BILLER"})
    @DisplayName("Integration: Real PostgreSQL constraint rejects negative amount for PAYER_PAYMENT")
    void testPostgresCheckConstraintNegativePaymentThrows() {
        PaymentLedgerEntry badEntry = new PaymentLedgerEntry(
                testClaim,
                testVoucher,
                testBatch,
                PaymentLedgerEntryType.PAYER_PAYMENT,
                new BigDecimal("-50.00"), // Violates ck_ledger_amount_sign!
                "ICN-IT-BAD",
                "CHK-IT-BAD",
                LocalDate.now(),
                "Invalid negative payment",
                "biller_integ"
        );

        assertThatThrownBy(() -> ledgerRepository.saveAndFlush(badEntry))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
