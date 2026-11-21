package com.claire.claims.dto;

import com.claire.claims.domain.ClaimStatus;
import com.claire.claims.domain.DenialCategory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public final class RevenueCycleAnalyticsDtos {

    private RevenueCycleAnalyticsDtos() {}

    // =========================================================================
    // 1. Executive Overview KPI Response
    // =========================================================================

    public record AnalyticsOverviewResponse(
            FinancialKpis financial,
            OperationalKpis operational,
            PerformanceKpis performance,
            OffsetDateTime generatedAt
    ) {}

    public record FinancialKpis(
            BigDecimal grossBilledCharges,
            BigDecimal legacyOpeningPayments,
            BigDecimal grossPostedPayerPayments,
            BigDecimal payerPaymentReversals,
            BigDecimal netRecordedPayments,
            BigDecimal verifiedContractualWriteOffs,
            BigDecimal adjudicatedPayerAr,
            BigDecimal patientAr,
            BigDecimal disputedAr,
            BigDecimal unadjudicatedSubmittedExposure,
            BigDecimal rejectedClaimExposure,
            BigDecimal draftClaimExposure,
            BigDecimal voidClaimExposure,
            BigDecimal legacyUnclassifiedExposure,
            BigDecimal totalCollectibleAr,
            BigDecimal totalOutstandingExposure,
            BigDecimal reconciliationDiscrepancy,
            boolean isBalanced
    ) {}

    public record OperationalKpis(
            int totalClaims,
            int submittedClaims,
            int acceptedClaims,
            int paidClaims,
            int partiallyPaidClaims,
            int deniedClaims,
            int appealedClaims,
            int rejectedClaims,
            int draftClaims,
            int voidClaims,
            int unresolvedExceptions,
            int totalBatches,
            int postedBatches
    ) {}

    public record PerformanceKpis(
            BigDecimal denialRate,
            BigDecimal rejectionRate,
            BigDecimal appealOverturnRate,
            BigDecimal appealResolutionRate,
            Double avgAppealResolutionDays,
            Double avgSubmissionToPaymentDays,
            BigDecimal eligibilitySuccessRate,
            BigDecimal arOver90DaysPercent
    ) {}

    // =========================================================================
    // 2. AR Aging Breakdown
    // =========================================================================

    public record ArAgingResponse(
            LocalDate asOfDate,
            String dateBasis,
            List<AgingBucketItem> buckets,
            BigDecimal totalCollectibleAr,
            BigDecimal totalDisputedAr,
            int totalActiveClaims
    ) {}

    public record AgingBucketItem(
            String bucketKey,
            String label,
            int minDays,
            Integer maxDays,
            int claimCount,
            BigDecimal adjudicatedPayerAr,
            BigDecimal patientAr,
            BigDecimal totalCollectibleAr,
            BigDecimal disputedAr,
            BigDecimal unclassifiedExposure
    ) {}

    // =========================================================================
    // 3. Payer Performance
    // =========================================================================

    public record PayerPerformanceItem(
            Long payerId,
            String payerCode,
            String payerName,
            int totalClaims,
            BigDecimal totalBilled,
            BigDecimal postedPayments,
            BigDecimal reversedPayments,
            BigDecimal netPayments,
            BigDecimal verifiedWriteOffs,
            BigDecimal adjudicatedPayerAr,
            BigDecimal patientAr,
            BigDecimal disputedExposure,
            int deniedClaimsCount,
            BigDecimal denialRate,
            Double avgProcessingDays,
            Map<String, BigDecimal> agingDistribution
    ) {}

    // =========================================================================
    // 4. Status Breakdown
    // =========================================================================

    public record StatusBreakdownItem(
            ClaimStatus status,
            String description,
            int claimCount,
            BigDecimal totalCharge,
            BigDecimal paidAmount,
            BigDecimal collectibleAr,
            BigDecimal contractualAdjustment
    ) {}

    // =========================================================================
    // 5. Payment Trends
    // =========================================================================

    public record PaymentTrendsResponse(
            List<MonthlyPaymentTrendItem> monthlyTrends,
            BigDecimal totalHistoricalLegacyOpening,
            BigDecimal grandTotalNetPayments,
            BigDecimal grandTotalWriteOffs
    ) {}

    public record MonthlyPaymentTrendItem(
            String period, // YYYY-MM
            BigDecimal postedPayerPayments,
            BigDecimal payerPaymentReversals,
            BigDecimal netPayerPayments,
            BigDecimal contractualWriteOffs,
            BigDecimal contractualReversals,
            BigDecimal netContractualAdjustments,
            int transactionCount
    ) {}

    // =========================================================================
    // 6. Denial Trends & Reason Analysis
    // =========================================================================

    public record DenialTrendsResponse(
            List<MonthlyDenialTrendItem> monthlyTrends,
            int totalDenialsRecorded,
            BigDecimal totalDenialAmount
    ) {}

    public record MonthlyDenialTrendItem(
            String period, // YYYY-MM
            int newDenialsCount,
            BigDecimal newDenialsAmount,
            int appealsFiledCount,
            int appealsOverturnedCount,
            int appealsUpheldCount
    ) {}

    public record DenialReasonItem(
            String code,
            DenialCategory category,
            String categoryDescription,
            String reason,
            String payerName,
            int count,
            BigDecimal totalAmount,
            BigDecimal percentageOfTotal
    ) {}

    // =========================================================================
    // 7. Appeal Performance
    // =========================================================================

    public record AppealPerformanceResponse(
            int totalAppeals,
            int pendingAppeals,
            int decidedAppeals,
            int overturnedCount,
            int partiallyOverturnedCount,
            int upheldCount,
            int withdrawnCount,
            BigDecimal overturnRate,
            BigDecimal resolutionRate,
            Double avgResolutionDays,
            int urgentDeadlinesCount,
            List<AppealLevelBreakdown> byLevel
    ) {}

    public record AppealLevelBreakdown(
            int level,
            String levelDescription,
            int totalCount,
            int overturnedCount,
            int upheldCount,
            int pendingCount
    ) {}

    // =========================================================================
    // 8. Eligibility Verification Performance
    // =========================================================================

    public record EligibilityPerformanceResponse(
            long totalChecks,
            long activeCoverageCount,
            long inactiveCoverageCount,
            long cachedChecksCount,
            BigDecimal activeCoveragePercent,
            BigDecimal cacheHitPercent
    ) {}

    // =========================================================================
    // 9. Priority Claims & Smart Insights
    // =========================================================================

    public record PriorityClaimItem(
            Long claimId,
            String claimNumber,
            String payerName,
            String patientName,
            ClaimStatus status,
            String priority, // HIGH, MEDIUM, LOW
            String category,
            String reason,
            String recommendedAction,
            BigDecimal outstandingAmount,
            long daysOutstanding,
            LocalDate deadline
    ) {}
}

