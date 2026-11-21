package com.claire.claims.service;

import com.claire.claims.domain.*;
import com.claire.claims.dto.AccountsReceivableDtos.AccountsReceivableSummaryResponse;
import com.claire.claims.dto.RevenueCycleAnalyticsDtos.*;
import com.claire.claims.repository.*;
import com.claire.claims.service.financial.AccountsReceivableService;
import com.claire.claims.service.financial.RevenueCycleAnalyticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.*;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RevenueCycleAnalyticsServiceTest {

    @Mock private ClaimRepository claimRepository;
    @Mock private PaymentLedgerRepository ledgerRepository;
    @Mock private ClaimDenialRepository denialRepository;
    @Mock private ClaimAppealRepository appealRepository;
    @Mock private EligibilityCheckRepository eligibilityRepository;
    @Mock private RemittanceBatchRepository batchRepository;
    @Mock private RemittanceClaimExceptionRepository exceptionRepository;
    @Mock private PayerRepository payerRepository;
    @Mock private AccountsReceivableService arService;

    private Clock fixedClock;
    private RevenueCycleAnalyticsService analyticsService;

    @BeforeEach
    void setUp() {
        // Fixed clock at 2026-11-20
        fixedClock = Clock.fixed(Instant.parse("2026-11-20T12:00:00Z"), ZoneOffset.UTC);
        analyticsService = new RevenueCycleAnalyticsService(
                claimRepository, ledgerRepository, denialRepository, appealRepository,
                eligibilityRepository, batchRepository, exceptionRepository, payerRepository,
                arService, fixedClock
        );
    }

    @Test
    @DisplayName("getOverview calculates financial, operational, and performance KPIs correctly")
    void testGetOverview() {
        AccountsReceivableSummaryResponse mockAr = new AccountsReceivableSummaryResponse(
                new BigDecimal("7640.00"), new BigDecimal("731.52"), new BigDecimal("540.00"),
                BigDecimal.ZERO, new BigDecimal("1271.52"), new BigDecimal("100.00"),
                new BigDecimal("534.00"), new BigDecimal("329.88"), new BigDecimal("1450.00"),
                new BigDecimal("3954.60"), new BigDecimal("180.00"), new BigDecimal("150.00"),
                new BigDecimal("1450.00"), new BigDecimal("82.88"), new BigDecimal("863.88"), new BigDecimal("6268.48"),
                true, BigDecimal.ZERO, 12, OffsetDateTime.now()
        );
        when(arService.getArSummary()).thenReturn(mockAr);

        Claim c1 = new Claim();
        c1.setId(1L);
        c1.setStatus(ClaimStatus.ACCEPTED);
        c1.setTotalCharge(new BigDecimal("295.00"));
        c1.setAllowedAmount(new BigDecimal("176.00"));
        c1.setSubmittedAt(OffsetDateTime.parse("2026-11-01T10:00:00Z"));

        Claim c2 = new Claim();
        c2.setId(2L);
        c2.setStatus(ClaimStatus.DENIED);
        c2.setTotalCharge(new BigDecimal("1450.00"));
        c2.setSubmittedAt(OffsetDateTime.parse("2026-10-15T10:00:00Z"));

        when(claimRepository.findAll()).thenReturn(List.of(c1, c2));
        when(exceptionRepository.findAll()).thenReturn(Collections.emptyList());
        when(batchRepository.findAll()).thenReturn(Collections.emptyList());
        when(appealRepository.findAll()).thenReturn(Collections.emptyList());
        when(eligibilityRepository.findAll()).thenReturn(Collections.emptyList());
        when(ledgerRepository.findAll()).thenReturn(Collections.emptyList());

        AnalyticsOverviewResponse overview = analyticsService.getOverview(null, null, null, null);

        assertThat(overview.financial().grossBilledCharges()).isEqualByComparingTo("7640.00");
        assertThat(overview.financial().totalCollectibleAr()).isEqualByComparingTo("863.88");
        assertThat(overview.financial().isBalanced()).isTrue();
        assertThat(overview.operational().totalClaims()).isEqualTo(2);
        assertThat(overview.operational().deniedClaims()).isEqualTo(1);
        assertThat(overview.operational().acceptedClaims()).isEqualTo(1);
    }

    @Test
    @DisplayName("getArAging segments active claims into 5 standard aging intervals and excludes VOID and DRAFT")
    void testArAgingBucketsAndExclusions() {
        // Claim 1: Submitted 10 days ago -> 0_30 bucket
        Claim c1 = new Claim();
        c1.setId(1L);
        c1.setStatus(ClaimStatus.ACCEPTED);
        c1.setAllowedAmount(new BigDecimal("200.00"));
        c1.setPaidAmount(BigDecimal.ZERO);
        c1.setPatientResponsibility(new BigDecimal("50.00"));
        c1.setSubmittedAt(OffsetDateTime.parse("2026-11-10T10:00:00Z"));

        // Claim 2: Submitted 45 days ago -> 31_60 bucket
        Claim c2 = new Claim();
        c2.setId(2L);
        c2.setStatus(ClaimStatus.PARTIALLY_PAID);
        c2.setAllowedAmount(new BigDecimal("500.00"));
        c2.setPaidAmount(new BigDecimal("300.00"));
        c2.setPatientResponsibility(new BigDecimal("50.00"));
        c2.setSubmittedAt(OffsetDateTime.parse("2026-10-06T10:00:00Z"));

        // Claim 3: VOID claim submitted 100 days ago -> MUST BE EXCLUDED
        Claim c3 = new Claim();
        c3.setId(3L);
        c3.setStatus(ClaimStatus.VOID);
        c3.setTotalCharge(new BigDecimal("1450.00"));
        c3.setSubmittedAt(OffsetDateTime.parse("2026-08-12T10:00:00Z"));

        // Claim 4: DRAFT claim -> MUST BE EXCLUDED
        Claim c4 = new Claim();
        c4.setId(4L);
        c4.setStatus(ClaimStatus.DRAFT);
        c4.setTotalCharge(new BigDecimal("150.00"));

        when(claimRepository.findAll()).thenReturn(List.of(c1, c2, c3, c4));

        ArAgingResponse response = analyticsService.getArAging(LocalDate.parse("2026-11-20"), null);

        assertThat(response.totalActiveClaims()).isEqualTo(2);
        // c1 collectible = (200 - 50) + 50 = 200.00 in 0_30
        // c2 collectible = (500 - 300 - 50) + 50 = 200.00 in 31_60
        assertThat(response.totalCollectibleAr()).isEqualByComparingTo("400.00");

        AgingBucketItem b0_30 = response.buckets().stream().filter(b -> b.bucketKey().equals("0_30")).findFirst().orElseThrow();
        assertThat(b0_30.claimCount()).isEqualTo(1);
        assertThat(b0_30.totalCollectibleAr()).isEqualByComparingTo("200.00");

        AgingBucketItem b31_60 = response.buckets().stream().filter(b -> b.bucketKey().equals("31_60")).findFirst().orElseThrow();
        assertThat(b31_60.claimCount()).isEqualTo(1);
        assertThat(b31_60.totalCollectibleAr()).isEqualByComparingTo("200.00");

        AgingBucketItem b120_plus = response.buckets().stream().filter(b -> b.bucketKey().equals("120_PLUS")).findFirst().orElseThrow();
        assertThat(b120_plus.claimCount()).isEqualTo(0);
        assertThat(b120_plus.totalCollectibleAr()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("exportCsv escapes values and sanitizes formula injection attempts")
    void testExportCsvSecurityAndFormulaSanitization() {
        Payer p = new Payer();
        p.setId(1L);
        p.setPayerCode("BCBS001");
        p.setName("=SUM(A1:A10)"); // Formula injection payload!

        when(payerRepository.findAll()).thenReturn(List.of(p));
        when(claimRepository.findAll()).thenReturn(Collections.emptyList());
        when(ledgerRepository.findAll()).thenReturn(Collections.emptyList());
        when(denialRepository.findAll()).thenReturn(Collections.emptyList());

        String csv = analyticsService.exportCsv("by-payer", null, null, null);

        // Verify CSV header
        assertThat(csv).startsWith("Payer Code,Payer Name");
        // Verify formula injection is neutralized by prepending single quote
        assertThat(csv).contains("BCBS001,'=SUM(A1:A10)");
    }
}

