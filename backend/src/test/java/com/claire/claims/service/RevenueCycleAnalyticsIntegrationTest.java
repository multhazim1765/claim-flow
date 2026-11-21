package com.claire.claims.service;

import com.claire.claims.dto.RevenueCycleAnalyticsDtos.*;
import com.claire.claims.service.financial.RevenueCycleAnalyticsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RevenueCycleAnalyticsIntegrationTest {

    @Autowired
    private RevenueCycleAnalyticsService analyticsService;

    @Test
    @DisplayName("Integration: getOverview retrieves verified reconciled data from live PostgreSQL database")
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void testLiveOverviewIntegration() {
        AnalyticsOverviewResponse overview = analyticsService.getOverview(null, null, null, null);

        assertThat(overview).isNotNull();
        assertThat(overview.financial().grossBilledCharges()).isEqualByComparingTo("7640.00");
        assertThat(overview.financial().netRecordedPayments()).isEqualByComparingTo("1271.52");
        assertThat(overview.financial().verifiedContractualWriteOffs()).isEqualByComparingTo("100.00");
        assertThat(overview.financial().isBalanced()).isTrue();
        assertThat(overview.financial().reconciliationDiscrepancy()).isEqualByComparingTo("0.00");
        assertThat(overview.operational().totalClaims()).isGreaterThanOrEqualTo(12);
    }

    @Test
    @DisplayName("Integration: getArAging aggregates active claims and excludes VOID claim")
    @WithMockUser(username = "biller", roles = {"BILLER"})
    void testLiveArAgingIntegration() {
        ArAgingResponse aging = analyticsService.getArAging(LocalDate.of(2026, 11, 20), null);

        assertThat(aging).isNotNull();
        assertThat(aging.buckets()).hasSize(5);
        // Collectible AR should match $863.88
        assertThat(aging.totalCollectibleAr()).isEqualByComparingTo("863.88");
        // VOID claim (charge 1450.00) must be excluded from active count
        assertThat(aging.totalActiveClaims()).isLessThanOrEqualTo(11);
    }

    @Test
    @DisplayName("Integration: getArByPayer calculates metrics across all seeded payers")
    @WithMockUser(username = "viewer", roles = {"VIEWER"})
    void testLivePayerPerformanceIntegration() {
        List<PayerPerformanceItem> items = analyticsService.getArByPayer();

        assertThat(items).isNotEmpty();
        PayerPerformanceItem aetna = items.stream()
                .filter(p -> "AETNA01".equalsIgnoreCase(p.payerCode()))
                .findFirst()
                .orElse(null);

        if (aetna != null) {
            assertThat(aetna.totalBilled()).isGreaterThan(BigDecimal.ZERO);
        }
    }

    @Test
    @DisplayName("Integration: exportCsv returns valid CSV string with headers")
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void testLiveCsvExportIntegration() {
        String csv = analyticsService.exportCsv("ar-aging", null, null, null);

        assertThat(csv).isNotNull();
        assertThat(csv).startsWith("Bucket Key,Label,Min Days");
        assertThat(csv).contains("0_30");
        assertThat(csv).contains("120_PLUS");
    }
}

