package com.claire.claims.service.financial;

import com.claire.claims.common.ApiExceptions.BusinessRuleException;
import com.claire.claims.domain.*;
import com.claire.claims.dto.AccountsReceivableDtos.AccountsReceivableSummaryResponse;
import com.claire.claims.dto.RevenueCycleAnalyticsDtos.*;
import com.claire.claims.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class RevenueCycleAnalyticsService {

    private final ClaimRepository claimRepository;
    private final PaymentLedgerRepository ledgerRepository;
    private final ClaimDenialRepository denialRepository;
    private final ClaimAppealRepository appealRepository;
    private final EligibilityCheckRepository eligibilityRepository;
    private final RemittanceBatchRepository batchRepository;
    private final RemittanceClaimExceptionRepository exceptionRepository;
    private final PayerRepository payerRepository;
    private final AccountsReceivableService arService;
    private final Clock clock;

    @Autowired
    public RevenueCycleAnalyticsService(ClaimRepository claimRepository,
                                        PaymentLedgerRepository ledgerRepository,
                                        ClaimDenialRepository denialRepository,
                                        ClaimAppealRepository appealRepository,
                                        EligibilityCheckRepository eligibilityRepository,
                                        RemittanceBatchRepository batchRepository,
                                        RemittanceClaimExceptionRepository exceptionRepository,
                                        PayerRepository payerRepository,
                                        AccountsReceivableService arService) {
        this(claimRepository, ledgerRepository, denialRepository, appealRepository,
                eligibilityRepository, batchRepository, exceptionRepository, payerRepository,
                arService, Clock.systemDefaultZone());
    }

    public RevenueCycleAnalyticsService(ClaimRepository claimRepository,
                                        PaymentLedgerRepository ledgerRepository,
                                        ClaimDenialRepository denialRepository,
                                        ClaimAppealRepository appealRepository,
                                        EligibilityCheckRepository eligibilityRepository,
                                        RemittanceBatchRepository batchRepository,
                                        RemittanceClaimExceptionRepository exceptionRepository,
                                        PayerRepository payerRepository,
                                        AccountsReceivableService arService,
                                        Clock clock) {
        this.claimRepository = claimRepository;
        this.ledgerRepository = ledgerRepository;
        this.denialRepository = denialRepository;
        this.appealRepository = appealRepository;
        this.eligibilityRepository = eligibilityRepository;
        this.batchRepository = batchRepository;
        this.exceptionRepository = exceptionRepository;
        this.payerRepository = payerRepository;
        this.arService = arService;
        this.clock = clock;
    }

    // =========================================================================
    // 1. Executive Overview
    // =========================================================================

    public AnalyticsOverviewResponse getOverview(LocalDate startDate, LocalDate endDate, Long payerId, Long providerId) {
        AccountsReceivableSummaryResponse ar = arService.getArSummary();
        List<Claim> claims = claimRepository.findAll();

        if (payerId != null) {
            claims = claims.stream().filter(c -> c.getPayer() != null && Objects.equals(c.getPayer().getId(), payerId)).toList();
        }
        if (providerId != null) {
            claims = claims.stream().filter(c -> c.getProvider() != null && Objects.equals(c.getProvider().getId(), providerId)).toList();
        }
        if (startDate != null) {
            claims = claims.stream().filter(c -> c.getServiceDateFrom() != null && !c.getServiceDateFrom().isBefore(startDate)).toList();
        }
        if (endDate != null) {
            claims = claims.stream().filter(c -> c.getServiceDateFrom() != null && !c.getServiceDateFrom().isAfter(endDate)).toList();
        }

        FinancialKpis financial = new FinancialKpis(
                ar.grossBilledCharges(),
                ar.legacyOpeningPayments(),
                ar.postedPayerPayments(),
                ar.payerPaymentReversals(),
                ar.netRecordedPayerPayments(),
                ar.verifiedContractualWriteOffs(),
                ar.adjudicatedPayerAr(),
                ar.patientAr(),
                ar.disputedAr(),
                ar.unadjudicatedSubmittedExposure(),
                ar.rejectedClaimExposure(),
                ar.draftClaimExposure(),
                ar.voidClaimExposure(),
                ar.legacyUnclassifiedBalances(),
                ar.totalCollectibleAr(),
                ar.totalOutstandingExposure(),
                ar.balanceDiscrepancy(),
                ar.isBalanced()
        );

        Map<ClaimStatus, Long> countsByStatus = claims.stream()
                .collect(Collectors.groupingBy(Claim::getStatus, Collectors.counting()));

        int totalClaims = claims.size();
        int submitted = countsByStatus.getOrDefault(ClaimStatus.SUBMITTED, 0L).intValue();
        int accepted = countsByStatus.getOrDefault(ClaimStatus.ACCEPTED, 0L).intValue();
        int paid = countsByStatus.getOrDefault(ClaimStatus.PAID, 0L).intValue();
        int partiallyPaid = countsByStatus.getOrDefault(ClaimStatus.PARTIALLY_PAID, 0L).intValue();
        int denied = countsByStatus.getOrDefault(ClaimStatus.DENIED, 0L).intValue();
        int appealed = countsByStatus.getOrDefault(ClaimStatus.APPEALED, 0L).intValue();
        int rejected = countsByStatus.getOrDefault(ClaimStatus.REJECTED, 0L).intValue();
        int draft = countsByStatus.getOrDefault(ClaimStatus.DRAFT, 0L).intValue();
        int voidClaims = countsByStatus.getOrDefault(ClaimStatus.VOID, 0L).intValue();

        int unresolvedExceptions = (int) exceptionRepository.findAll().stream()
                .filter(e -> "UNRESOLVED".equalsIgnoreCase(e.getStatus())).count();
        List<RemittanceBatch> batches = batchRepository.findAll();
        int totalBatches = batches.size();
        int postedBatches = (int) batches.stream().filter(b -> b.getStatus() == RemittanceBatchStatus.POSTED).count();

        OperationalKpis operational = new OperationalKpis(
                totalClaims, submitted, accepted, paid, partiallyPaid, denied, appealed,
                rejected, draft, voidClaims, unresolvedExceptions, totalBatches, postedBatches
        );

        // Performance Calculations
        int adjudicatedBase = accepted + paid + partiallyPaid + denied + appealed;
        BigDecimal denialRate = adjudicatedBase > 0
                ? BigDecimal.valueOf(denied + appealed)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(adjudicatedBase), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        int submissionBase = totalClaims - draft - voidClaims;
        BigDecimal rejectionRate = submissionBase > 0
                ? BigDecimal.valueOf(rejected)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(submissionBase), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        List<ClaimAppeal> appeals = appealRepository.findAll();
        long decidedAppeals = appeals.stream().filter(a -> a.getOutcome() != null).count();
        long overturnedAppeals = appeals.stream()
                .filter(a -> a.getOutcome() == AppealOutcome.OVERTURNED || a.getOutcome() == AppealOutcome.PARTIAL)
                .count();

        BigDecimal appealOverturnRate = decidedAppeals > 0
                ? BigDecimal.valueOf(overturnedAppeals)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(decidedAppeals), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal appealResolutionRate = !appeals.isEmpty()
                ? BigDecimal.valueOf(decidedAppeals)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(appeals.size()), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        Double avgAppealDays = appeals.stream()
                .filter(a -> a.getDecidedOn() != null && a.getFiledOn() != null)
                .mapToLong(a -> ChronoUnit.DAYS.between(a.getFiledOn(), a.getDecidedOn()))
                .average().orElse(0.0);

        List<EligibilityCheck> eligibilityChecks = eligibilityRepository.findAll();
        long activeCoverage = eligibilityChecks.stream().filter(EligibilityCheck::isActive).count();
        BigDecimal eligibilitySuccessRate = !eligibilityChecks.isEmpty()
                ? BigDecimal.valueOf(activeCoverage)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(eligibilityChecks.size()), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // AR over 90 days percent
        ArAgingResponse aging = getArAging(LocalDate.now(clock), payerId);
        BigDecimal arOver90 = aging.buckets().stream()
                .filter(b -> b.minDays() >= 91)
                .map(AgingBucketItem::totalCollectibleAr)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal arOver90Percent = aging.totalCollectibleAr().signum() > 0
                ? arOver90.multiply(BigDecimal.valueOf(100)).divide(aging.totalCollectibleAr(), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // Avg submission to payment
        List<PaymentLedgerEntry> payments = ledgerRepository.findAll().stream()
                .filter(l -> l.getEntryType() == PaymentLedgerEntryType.PAYER_PAYMENT).toList();
        Double avgSubToPayDays = payments.stream()
                .filter(p -> p.getClaim() != null && p.getClaim().getSubmittedAt() != null)
                .mapToLong(p -> ChronoUnit.DAYS.between(p.getClaim().getSubmittedAt().toLocalDate(), p.getPostedAt().toLocalDate()))
                .average().orElse(0.0);

        PerformanceKpis performance = new PerformanceKpis(
                denialRate, rejectionRate, appealOverturnRate, appealResolutionRate,
                avgAppealDays, avgSubToPayDays, eligibilitySuccessRate, arOver90Percent
        );

        return new AnalyticsOverviewResponse(financial, operational, performance, OffsetDateTime.now(clock));
    }

    // =========================================================================
    // 2. AR Aging Breakdown
    // =========================================================================

    public ArAgingResponse getArAging(LocalDate asOfDate, Long payerId) {
        LocalDate asOf = asOfDate != null ? asOfDate : LocalDate.now(clock);
        List<Claim> claims = claimRepository.findAll();
        if (payerId != null) {
            claims = claims.stream().filter(c -> c.getPayer() != null && Objects.equals(c.getPayer().getId(), payerId)).toList();
        }

        // Initialize 5 standard buckets
        Map<String, AgingAccumulator> bucketMap = new LinkedHashMap<>();
        bucketMap.put("0_30", new AgingAccumulator("0–30 days", 0, 30));
        bucketMap.put("31_60", new AgingAccumulator("31–60 days", 31, 60));
        bucketMap.put("61_90", new AgingAccumulator("61–90 days", 61, 90));
        bucketMap.put("91_120", new AgingAccumulator("91–120 days", 91, 120));
        bucketMap.put("120_PLUS", new AgingAccumulator("120+ days", 121, null));

        BigDecimal totalCollectibleAr = BigDecimal.ZERO;
        BigDecimal totalDisputedAr = BigDecimal.ZERO;
        int activeCount = 0;

        for (Claim c : claims) {
            ClaimStatus status = c.getStatus();
            // VOID and DRAFT claims excluded from active AR aging
            if (status == ClaimStatus.VOID || status == ClaimStatus.DRAFT) {
                continue;
            }

            LocalDate originDate = getAgingDateOrigin(c);
            long days = originDate != null ? ChronoUnit.DAYS.between(originDate, asOf) : 0;
            if (days < 0) days = 0;

            String key = getBucketKey(days);
            AgingAccumulator acc = bucketMap.get(key);

            BigDecimal allowed = c.getAllowedAmount() != null ? c.getAllowedAmount() : BigDecimal.ZERO;
            BigDecimal paid = c.getPaidAmount() != null ? c.getPaidAmount() : BigDecimal.ZERO;
            BigDecimal patientResp = c.getPatientResponsibility() != null ? c.getPatientResponsibility() : BigDecimal.ZERO;
            BigDecimal charge = c.getTotalCharge() != null ? c.getTotalCharge() : BigDecimal.ZERO;

            BigDecimal payerAr = BigDecimal.ZERO;
            if ((status == ClaimStatus.ACCEPTED || status == ClaimStatus.PARTIALLY_PAID) && allowed.signum() > 0) {
                BigDecimal obligation = allowed.subtract(paid).subtract(patientResp);
                if (obligation.signum() > 0) {
                    payerAr = obligation;
                }
            }

            BigDecimal disputed = (status == ClaimStatus.DENIED || status == ClaimStatus.APPEALED) ? charge : BigDecimal.ZERO;
            BigDecimal collectible = payerAr.add(patientResp);

            acc.claimCount++;
            acc.adjudicatedPayerAr = acc.adjudicatedPayerAr.add(payerAr);
            acc.patientAr = acc.patientAr.add(patientResp);
            acc.totalCollectibleAr = acc.totalCollectibleAr.add(collectible);
            acc.disputedAr = acc.disputedAr.add(disputed);

            totalCollectibleAr = totalCollectibleAr.add(collectible);
            totalDisputedAr = totalDisputedAr.add(disputed);
            activeCount++;
        }

        List<AgingBucketItem> bucketItems = bucketMap.entrySet().stream()
                .map(e -> new AgingBucketItem(
                        e.getKey(),
                        e.getValue().label,
                        e.getValue().minDays,
                        e.getValue().maxDays,
                        e.getValue().claimCount,
                        e.getValue().adjudicatedPayerAr.setScale(2, RoundingMode.HALF_UP),
                        e.getValue().patientAr.setScale(2, RoundingMode.HALF_UP),
                        e.getValue().totalCollectibleAr.setScale(2, RoundingMode.HALF_UP),
                        e.getValue().disputedAr.setScale(2, RoundingMode.HALF_UP),
                        BigDecimal.ZERO
                )).toList();

        return new ArAgingResponse(
                asOf,
                "Claim submission timestamp where available; otherwise service date or creation timestamp",
                bucketItems,
                totalCollectibleAr.setScale(2, RoundingMode.HALF_UP),
                totalDisputedAr.setScale(2, RoundingMode.HALF_UP),
                activeCount
        );
    }

    private LocalDate getAgingDateOrigin(Claim c) {
        if (c.getSubmittedAt() != null) return c.getSubmittedAt().toLocalDate();
        if (c.getServiceDateFrom() != null) return c.getServiceDateFrom();
        if (c.getCreatedAt() != null) return c.getCreatedAt().toLocalDate();
        return null;
    }

    private String getBucketKey(long days) {
        if (days <= 30) return "0_30";
        if (days <= 60) return "31_60";
        if (days <= 90) return "61_90";
        if (days <= 120) return "91_120";
        return "120_PLUS";
    }

    // =========================================================================
    // 3. Payer Performance
    // =========================================================================

    public List<PayerPerformanceItem> getArByPayer() {
        List<Payer> payers = payerRepository.findAll();
        List<Claim> allClaims = claimRepository.findAll();
        List<PaymentLedgerEntry> allLedger = ledgerRepository.findAll();
        List<ClaimDenial> allDenials = denialRepository.findAll();
        LocalDate now = LocalDate.now(clock);

        List<PayerPerformanceItem> items = new ArrayList<>();

        for (Payer p : payers) {
            List<Claim> pClaims = allClaims.stream()
                    .filter(c -> c.getPayer() != null && Objects.equals(c.getPayer().getId(), p.getId()))
                    .toList();

            BigDecimal totalBilled = pClaims.stream()
                    .map(c -> c.getTotalCharge() != null ? c.getTotalCharge() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            Set<Long> claimIds = pClaims.stream().map(Claim::getId).collect(Collectors.toSet());
            List<PaymentLedgerEntry> pLedger = allLedger.stream()
                    .filter(l -> l.getClaim() != null && claimIds.contains(l.getClaim().getId()))
                    .toList();

            BigDecimal posted = pLedger.stream()
                    .filter(l -> l.getEntryType() == PaymentLedgerEntryType.PAYER_PAYMENT)
                    .map(PaymentLedgerEntry::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal reversed = pLedger.stream()
                    .filter(l -> l.getEntryType() == PaymentLedgerEntryType.PAYER_REVERSAL)
                    .map(PaymentLedgerEntry::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal legacy = pLedger.stream()
                    .filter(l -> l.getEntryType() == PaymentLedgerEntryType.LEGACY_INITIAL_BALANCE)
                    .map(PaymentLedgerEntry::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal netPayments = legacy.add(posted).add(reversed);

            BigDecimal writeOffs = pLedger.stream()
                    .filter(l -> l.getEntryType() == PaymentLedgerEntryType.CONTRACTUAL_WRITE_OFF)
                    .map(PaymentLedgerEntry::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal writeOffReversals = pLedger.stream()
                    .filter(l -> l.getEntryType() == PaymentLedgerEntryType.CONTRACTUAL_ADJUSTMENT_REVERSAL)
                    .map(PaymentLedgerEntry::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal netWriteOffs = writeOffs.add(writeOffReversals);

            BigDecimal adjudicatedPayerAr = BigDecimal.ZERO;
            BigDecimal patientAr = BigDecimal.ZERO;
            BigDecimal disputed = BigDecimal.ZERO;
            int deniedCount = 0;

            Map<String, BigDecimal> agingMap = new HashMap<>();
            agingMap.put("0_30", BigDecimal.ZERO);
            agingMap.put("31_60", BigDecimal.ZERO);
            agingMap.put("61_90", BigDecimal.ZERO);
            agingMap.put("91_plus", BigDecimal.ZERO);

            for (Claim c : pClaims) {
                ClaimStatus st = c.getStatus();
                if (st == ClaimStatus.VOID || st == ClaimStatus.DRAFT) continue;

                BigDecimal allowed = c.getAllowedAmount() != null ? c.getAllowedAmount() : BigDecimal.ZERO;
                BigDecimal paid = c.getPaidAmount() != null ? c.getPaidAmount() : BigDecimal.ZERO;
                BigDecimal pr = c.getPatientResponsibility() != null ? c.getPatientResponsibility() : BigDecimal.ZERO;

                BigDecimal pOwed = BigDecimal.ZERO;
                if ((st == ClaimStatus.ACCEPTED || st == ClaimStatus.PARTIALLY_PAID) && allowed.signum() > 0) {
                    BigDecimal diff = allowed.subtract(paid).subtract(pr);
                    if (diff.signum() > 0) pOwed = diff;
                }

                adjudicatedPayerAr = adjudicatedPayerAr.add(pOwed);
                patientAr = patientAr.add(pr);

                if (st == ClaimStatus.DENIED || st == ClaimStatus.APPEALED) {
                    disputed = disputed.add(c.getTotalCharge() != null ? c.getTotalCharge() : BigDecimal.ZERO);
                    deniedCount++;
                }

                LocalDate origin = getAgingDateOrigin(c);
                long days = origin != null ? ChronoUnit.DAYS.between(origin, now) : 0;
                BigDecimal collectible = pOwed.add(pr);
                if (days <= 30) {
                    agingMap.put("0_30", agingMap.get("0_30").add(collectible));
                } else if (days <= 60) {
                    agingMap.put("31_60", agingMap.get("31_60").add(collectible));
                } else if (days <= 90) {
                    agingMap.put("61_90", agingMap.get("61_90").add(collectible));
                } else {
                    agingMap.put("91_plus", agingMap.get("91_plus").add(collectible));
                }
            }

            int pAdjudicated = (int) pClaims.stream().filter(c -> c.getStatus() == ClaimStatus.ACCEPTED
                    || c.getStatus() == ClaimStatus.PAID
                    || c.getStatus() == ClaimStatus.PARTIALLY_PAID
                    || c.getStatus() == ClaimStatus.DENIED
                    || c.getStatus() == ClaimStatus.APPEALED).count();

            BigDecimal denialRate = pAdjudicated > 0
                    ? BigDecimal.valueOf(deniedCount).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(pAdjudicated), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            Double avgDays = pClaims.stream()
                    .filter(c -> c.getSubmittedAt() != null && c.getUpdatedAt() != null && c.getStatus() != ClaimStatus.SUBMITTED)
                    .mapToLong(c -> ChronoUnit.DAYS.between(c.getSubmittedAt().toLocalDate(), c.getUpdatedAt().toLocalDate()))
                    .average().orElse(0.0);

            items.add(new PayerPerformanceItem(
                    p.getId(),
                    p.getPayerCode(),
                    p.getName(),
                    pClaims.size(),
                    totalBilled.setScale(2, RoundingMode.HALF_UP),
                    posted.setScale(2, RoundingMode.HALF_UP),
                    reversed.setScale(2, RoundingMode.HALF_UP),
                    netPayments.setScale(2, RoundingMode.HALF_UP),
                    netWriteOffs.setScale(2, RoundingMode.HALF_UP),
                    adjudicatedPayerAr.setScale(2, RoundingMode.HALF_UP),
                    patientAr.setScale(2, RoundingMode.HALF_UP),
                    disputed.setScale(2, RoundingMode.HALF_UP),
                    deniedCount,
                    denialRate,
                    avgDays,
                    agingMap
            ));
        }

        return items;
    }

    // =========================================================================
    // 4. Status Breakdown
    // =========================================================================

    public List<StatusBreakdownItem> getArByStatus() {
        List<Claim> claims = claimRepository.findAll();
        Map<ClaimStatus, List<Claim>> grouped = claims.stream().collect(Collectors.groupingBy(Claim::getStatus));

        List<StatusBreakdownItem> items = new ArrayList<>();
        for (ClaimStatus st : ClaimStatus.values()) {
            List<Claim> statusClaims = grouped.getOrDefault(st, Collections.emptyList());
            BigDecimal totalCharge = statusClaims.stream()
                    .map(c -> c.getTotalCharge() != null ? c.getTotalCharge() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal paid = statusClaims.stream()
                    .map(c -> c.getPaidAmount() != null ? c.getPaidAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal contractual = statusClaims.stream()
                    .map(c -> c.getContractualAdjustment() != null ? c.getContractualAdjustment() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal collectible = BigDecimal.ZERO;
            if (st == ClaimStatus.ACCEPTED || st == ClaimStatus.PARTIALLY_PAID) {
                for (Claim c : statusClaims) {
                    BigDecimal allowed = c.getAllowedAmount() != null ? c.getAllowedAmount() : BigDecimal.ZERO;
                    BigDecimal cPaid = c.getPaidAmount() != null ? c.getPaidAmount() : BigDecimal.ZERO;
                    BigDecimal pr = c.getPatientResponsibility() != null ? c.getPatientResponsibility() : BigDecimal.ZERO;
                    if (allowed.signum() > 0) {
                        BigDecimal pOwed = allowed.subtract(cPaid).subtract(pr);
                        if (pOwed.signum() > 0) collectible = collectible.add(pOwed);
                    }
                    collectible = collectible.add(pr);
                }
            } else if (st == ClaimStatus.PAID) {
                for (Claim c : statusClaims) {
                    BigDecimal pr = c.getPatientResponsibility() != null ? c.getPatientResponsibility() : BigDecimal.ZERO;
                    collectible = collectible.add(pr);
                }
            }

            items.add(new StatusBreakdownItem(
                    st,
                    st.name().replace('_', ' '),
                    statusClaims.size(),
                    totalCharge.setScale(2, RoundingMode.HALF_UP),
                    paid.setScale(2, RoundingMode.HALF_UP),
                    collectible.setScale(2, RoundingMode.HALF_UP),
                    contractual.setScale(2, RoundingMode.HALF_UP)
            ));
        }

        return items;
    }

    // =========================================================================
    // 5. Payment Trends
    // =========================================================================

    public PaymentTrendsResponse getPaymentTrends(Integer monthsLimit) {
        List<PaymentLedgerEntry> entries = ledgerRepository.findAll();
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM");

        Map<String, List<PaymentLedgerEntry>> groupedByMonth = new TreeMap<>();
        BigDecimal legacyOpeningTotal = BigDecimal.ZERO;

        for (PaymentLedgerEntry e : entries) {
            if (e.getEntryType() == PaymentLedgerEntryType.LEGACY_INITIAL_BALANCE) {
                legacyOpeningTotal = legacyOpeningTotal.add(e.getAmount());
                continue;
            }
            String period = e.getPostedAt().format(dtf);
            groupedByMonth.computeIfAbsent(period, k -> new ArrayList<>()).add(e);
        }

        List<MonthlyPaymentTrendItem> trends = new ArrayList<>();
        BigDecimal grandTotalNetPayments = BigDecimal.ZERO;
        BigDecimal grandTotalWriteOffs = BigDecimal.ZERO;

        for (Map.Entry<String, List<PaymentLedgerEntry>> entry : groupedByMonth.entrySet()) {
            String period = entry.getKey();
            List<PaymentLedgerEntry> list = entry.getValue();

            BigDecimal posted = list.stream()
                    .filter(l -> l.getEntryType() == PaymentLedgerEntryType.PAYER_PAYMENT)
                    .map(PaymentLedgerEntry::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal reversed = list.stream()
                    .filter(l -> l.getEntryType() == PaymentLedgerEntryType.PAYER_REVERSAL)
                    .map(PaymentLedgerEntry::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal netPaid = posted.add(reversed);

            BigDecimal writeOffs = list.stream()
                    .filter(l -> l.getEntryType() == PaymentLedgerEntryType.CONTRACTUAL_WRITE_OFF)
                    .map(PaymentLedgerEntry::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal coReversals = list.stream()
                    .filter(l -> l.getEntryType() == PaymentLedgerEntryType.CONTRACTUAL_ADJUSTMENT_REVERSAL)
                    .map(PaymentLedgerEntry::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal netWriteOffs = writeOffs.add(coReversals);

            grandTotalNetPayments = grandTotalNetPayments.add(netPaid);
            grandTotalWriteOffs = grandTotalWriteOffs.add(netWriteOffs);

            trends.add(new MonthlyPaymentTrendItem(
                    period,
                    posted.setScale(2, RoundingMode.HALF_UP),
                    reversed.setScale(2, RoundingMode.HALF_UP),
                    netPaid.setScale(2, RoundingMode.HALF_UP),
                    writeOffs.setScale(2, RoundingMode.HALF_UP),
                    coReversals.setScale(2, RoundingMode.HALF_UP),
                    netWriteOffs.setScale(2, RoundingMode.HALF_UP),
                    list.size()
            ));
        }

        if (monthsLimit != null && monthsLimit > 0 && trends.size() > monthsLimit) {
            trends = trends.subList(trends.size() - monthsLimit, trends.size());
        }

        return new PaymentTrendsResponse(
                trends,
                legacyOpeningTotal.setScale(2, RoundingMode.HALF_UP),
                grandTotalNetPayments.setScale(2, RoundingMode.HALF_UP),
                grandTotalWriteOffs.setScale(2, RoundingMode.HALF_UP)
        );
    }

    // =========================================================================
    // 6. Denial Trends & Reason Analysis
    // =========================================================================

    public DenialTrendsResponse getDenialTrends() {
        List<ClaimDenial> denials = denialRepository.findAll();
        List<ClaimAppeal> appeals = appealRepository.findAll();
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM");

        Map<String, List<ClaimDenial>> denialsByMonth = new TreeMap<>();
        for (ClaimDenial d : denials) {
            String period = d.getCreatedAt().format(dtf);
            denialsByMonth.computeIfAbsent(period, k -> new ArrayList<>()).add(d);
        }

        Map<String, List<ClaimAppeal>> appealsByMonth = new TreeMap<>();
        for (ClaimAppeal a : appeals) {
            String period = a.getCreatedAt().format(dtf);
            appealsByMonth.computeIfAbsent(period, k -> new ArrayList<>()).add(a);
        }

        Set<String> allPeriods = new TreeSet<>();
        allPeriods.addAll(denialsByMonth.keySet());
        allPeriods.addAll(appealsByMonth.keySet());

        List<MonthlyDenialTrendItem> items = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (String period : allPeriods) {
            List<ClaimDenial> mDenials = denialsByMonth.getOrDefault(period, Collections.emptyList());
            List<ClaimAppeal> mAppeals = appealsByMonth.getOrDefault(period, Collections.emptyList());

            BigDecimal mAmount = mDenials.stream()
                    .map(d -> d.getAmount() != null ? d.getAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            totalAmount = totalAmount.add(mAmount);

            int overturned = (int) mAppeals.stream().filter(a -> a.getOutcome() == AppealOutcome.OVERTURNED || a.getOutcome() == AppealOutcome.PARTIAL).count();
            int upheld = (int) mAppeals.stream().filter(a -> a.getOutcome() == AppealOutcome.UPHELD).count();

            items.add(new MonthlyDenialTrendItem(
                    period,
                    mDenials.size(),
                    mAmount.setScale(2, RoundingMode.HALF_UP),
                    mAppeals.size(),
                    overturned,
                    upheld
            ));
        }

        return new DenialTrendsResponse(items, denials.size(), totalAmount.setScale(2, RoundingMode.HALF_UP));
    }

    public List<DenialReasonItem> getDenialsByReason() {
        List<ClaimDenial> denials = denialRepository.findAll();
        if (denials.isEmpty()) return Collections.emptyList();

        BigDecimal grandTotal = denials.stream()
                .map(d -> d.getAmount() != null ? d.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, List<ClaimDenial>> grouped = denials.stream()
                .collect(Collectors.groupingBy(d -> d.getDenialCode() + "|" + d.getDenialCategory()));

        List<DenialReasonItem> items = new ArrayList<>();
        for (List<ClaimDenial> list : grouped.values()) {
            ClaimDenial first = list.get(0);
            BigDecimal sum = list.stream()
                    .map(d -> d.getAmount() != null ? d.getAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal pct = grandTotal.signum() > 0
                    ? sum.multiply(BigDecimal.valueOf(100)).divide(grandTotal, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            items.add(new DenialReasonItem(
                    first.getDenialCode(),
                    first.getDenialCategory(),
                    first.getDenialCategory().name().replace('_', ' '),
                    first.getReason(),
                    first.getPayer() != null ? first.getPayer().getName() : "All Payers",
                    list.size(),
                    sum.setScale(2, RoundingMode.HALF_UP),
                    pct
            ));
        }

        items.sort(Comparator.comparing(DenialReasonItem::totalAmount).reversed());
        return items;
    }

    // =========================================================================
    // 7. Appeal Performance
    // =========================================================================

    public AppealPerformanceResponse getAppealPerformance() {
        List<ClaimAppeal> appeals = appealRepository.findAll();
        LocalDate now = LocalDate.now(clock);

        int total = appeals.size();
        int pending = (int) appeals.stream().filter(a -> a.getOutcome() == null).count();
        int decided = total - pending;

        int overturned = (int) appeals.stream().filter(a -> a.getOutcome() == AppealOutcome.OVERTURNED).count();
        int partial = (int) appeals.stream().filter(a -> a.getOutcome() == AppealOutcome.PARTIAL).count();
        int upheld = (int) appeals.stream().filter(a -> a.getOutcome() == AppealOutcome.UPHELD).count();
        int withdrawn = (int) appeals.stream().filter(a -> a.getOutcome() == AppealOutcome.WITHDRAWN).count();

        BigDecimal overturnRate = decided > 0
                ? BigDecimal.valueOf(overturned + partial)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(decided), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal resolutionRate = total > 0
                ? BigDecimal.valueOf(decided)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        Double avgDays = appeals.stream()
                .filter(a -> a.getDecidedOn() != null && a.getFiledOn() != null)
                .mapToLong(a -> ChronoUnit.DAYS.between(a.getFiledOn(), a.getDecidedOn()))
                .average().orElse(0.0);

        int urgentDeadlines = (int) appeals.stream()
                .filter(a -> a.getOutcome() == null && a.getDeadline() != null && !a.getDeadline().isAfter(now.plusDays(7)))
                .count();

        Map<Integer, List<ClaimAppeal>> byLevelMap = appeals.stream()
                .collect(Collectors.groupingBy(ClaimAppeal::getLevel));

        List<AppealLevelBreakdown> levelBreakdown = new ArrayList<>();
        for (int lvl = 1; lvl <= 3; lvl++) {
            List<ClaimAppeal> lvlList = byLevelMap.getOrDefault(lvl, Collections.emptyList());
            int lOver = (int) lvlList.stream().filter(a -> a.getOutcome() == AppealOutcome.OVERTURNED || a.getOutcome() == AppealOutcome.PARTIAL).count();
            int lUp = (int) lvlList.stream().filter(a -> a.getOutcome() == AppealOutcome.UPHELD).count();
            int lPend = (int) lvlList.stream().filter(a -> a.getOutcome() == null).count();

            levelBreakdown.add(new AppealLevelBreakdown(
                    lvl,
                    lvl == 1 ? "Level 1: Redetermination" : lvl == 2 ? "Level 2: Reconsideration" : "Level 3: ALJ Hearing",
                    lvlList.size(),
                    lOver,
                    lUp,
                    lPend
            ));
        }

        return new AppealPerformanceResponse(
                total, pending, decided, overturned, partial, upheld, withdrawn,
                overturnRate, resolutionRate, avgDays, urgentDeadlines, levelBreakdown
        );
    }

    // =========================================================================
    // 8. Eligibility Performance
    // =========================================================================

    public EligibilityPerformanceResponse getEligibilityPerformance() {
        List<EligibilityCheck> checks = eligibilityRepository.findAll();
        long total = checks.size();
        long active = checks.stream().filter(EligibilityCheck::isActive).count();
        long inactive = total - active;
        long cached = checks.stream().filter(EligibilityCheck::isCached).count();

        BigDecimal activePct = total > 0
                ? BigDecimal.valueOf(active).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal cachePct = total > 0
                ? BigDecimal.valueOf(cached).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return new EligibilityPerformanceResponse(total, active, inactive, cached, activePct, cachePct);
    }

    // =========================================================================
    // 9. Priority Claims & Smart Insights
    // =========================================================================

    public List<PriorityClaimItem> getPriorityClaims() {
        List<Claim> claims = claimRepository.findAll();
        List<ClaimAppeal> appeals = appealRepository.findAll();
        LocalDate now = LocalDate.now(clock);

        List<PriorityClaimItem> priorityList = new ArrayList<>();

        for (Claim c : claims) {
            if (c.getStatus() == ClaimStatus.VOID) continue;

            LocalDate origin = getAgingDateOrigin(c);
            long days = origin != null ? ChronoUnit.DAYS.between(origin, now) : 0;
            if (days < 0) days = 0;

            BigDecimal charge = c.getTotalCharge() != null ? c.getTotalCharge() : BigDecimal.ZERO;
            BigDecimal paid = c.getPaidAmount() != null ? c.getPaidAmount() : BigDecimal.ZERO;
            BigDecimal co = c.getContractualAdjustment() != null ? c.getContractualAdjustment() : BigDecimal.ZERO;
            BigDecimal outstanding = charge.subtract(paid).subtract(co).max(BigDecimal.ZERO);

            // Rule 1: High-value aging > 120 days
            if (days > 120 && outstanding.compareTo(BigDecimal.valueOf(100)) > 0) {
                priorityList.add(new PriorityClaimItem(
                        c.getId(),
                        c.getClaimNumber(),
                        c.getPayer() != null ? c.getPayer().getName() : "Unknown Payer",
                        c.getPatient() != null ? c.getPatient().getDisplayName() : "Unknown Patient",
                        c.getStatus(),
                        "HIGH",
                        "AGING_OVER_120",
                        "Claim has been outstanding for " + days + " days with $" + outstanding + " balance.",
                        "Initiate immediate payer escalation and verify timely filing limits.",
                        outstanding,
                        days,
                        null
                ));
            }

            // Rule 2: Pending appeal approaching deadline
            Optional<ClaimAppeal> pendingAppeal = appeals.stream()
                    .filter(a -> a.getClaim() != null && Objects.equals(a.getClaim().getId(), c.getId()) && a.getOutcome() == null)
                    .findFirst();

            if (pendingAppeal.isPresent()) {
                ClaimAppeal a = pendingAppeal.get();
                long deadlineDays = a.getDeadline() != null ? ChronoUnit.DAYS.between(now, a.getDeadline()) : 999;
                if (deadlineDays <= 14) {
                    priorityList.add(new PriorityClaimItem(
                            c.getId(),
                            c.getClaimNumber(),
                            c.getPayer() != null ? c.getPayer().getName() : "Unknown Payer",
                            c.getPatient() != null ? c.getPatient().getDisplayName() : "Unknown Patient",
                            c.getStatus(),
                            "HIGH",
                            "APPEAL_DEADLINE_APPROACHING",
                            "Level " + a.getLevel() + " appeal response window closes in " + deadlineDays + " days.",
                            "Contact payer appeals division or prepare documentation for next-level escalation.",
                            outstanding,
                            days,
                            a.getDeadline()
                    ));
                }
            }

            // Rule 3: High-value unadjudicated claim (> $1,000)
            if (c.getStatus() == ClaimStatus.SUBMITTED && c.getTotalCharge().compareTo(BigDecimal.valueOf(1000)) > 0 && days > 15) {
                priorityList.add(new PriorityClaimItem(
                        c.getId(),
                        c.getClaimNumber(),
                        c.getPayer() != null ? c.getPayer().getName() : "Unknown Payer",
                        c.getPatient() != null ? c.getPatient().getDisplayName() : "Unknown Patient",
                        c.getStatus(),
                        "MEDIUM",
                        "HIGH_VALUE_UNADJUDICATED",
                        "High-charge claim of $" + c.getTotalCharge() + " submitted " + days + " days ago without remittance.",
                        "Verify clearinghouse EDI 277 acknowledgement and poll payer for 835 ERA.",
                        c.getTotalCharge(),
                        days,
                        null
                ));
            }

            // Rule 4: Collectible AR between 61-120 days
            if (days >= 61 && days <= 120 && outstanding.compareTo(BigDecimal.valueOf(50)) > 0
                    && (c.getStatus() == ClaimStatus.ACCEPTED || c.getStatus() == ClaimStatus.PARTIALLY_PAID)) {
                priorityList.add(new PriorityClaimItem(
                        c.getId(),
                        c.getClaimNumber(),
                        c.getPayer() != null ? c.getPayer().getName() : "Unknown Payer",
                        c.getPatient() != null ? c.getPatient().getDisplayName() : "Unknown Patient",
                        c.getStatus(),
                        "MEDIUM",
                        "AGING_61_120",
                        "Adjudicated claim balance of $" + outstanding + " aging in " + days + " days bracket.",
                        "Issue patient billing statement or follow up on pending secondary insurance claim.",
                        outstanding,
                        days,
                        null
                ));
            }

            // Rule 5: Legacy inconsistency (PAID with uncollected patient responsibility)
            if (c.getStatus() == ClaimStatus.PAID && c.getPatientResponsibility() != null && c.getPatientResponsibility().signum() > 0) {
                priorityList.add(new PriorityClaimItem(
                        c.getId(),
                        c.getClaimNumber(),
                        c.getPayer() != null ? c.getPayer().getName() : "Unknown Payer",
                        c.getPatient() != null ? c.getPatient().getDisplayName() : "Unknown Patient",
                        c.getStatus(),
                        "LOW",
                        "LEGACY_PATIENT_RESPONSIBILITY",
                        "Claim marked PAID in legacy system with $" + c.getPatientResponsibility() + " outstanding patient obligation.",
                        "Review legacy records to confirm whether patient payment was collected outside ClaimFlow.",
                        c.getPatientResponsibility(),
                        days,
                        null
                ));
            }
        }

        // Sort: HIGH first, then MEDIUM, then LOW
        priorityList.sort(Comparator.comparing((PriorityClaimItem p) -> {
            if ("HIGH".equalsIgnoreCase(p.priority())) return 1;
            if ("MEDIUM".equalsIgnoreCase(p.priority())) return 2;
            return 3;
        }).thenComparing(PriorityClaimItem::outstandingAmount, Comparator.reverseOrder()));

        return priorityList;
    }

    // =========================================================================
    // 10. CSV Export Engine
    // =========================================================================

    public String exportCsv(String type, Long payerId, LocalDate startDate, LocalDate endDate) {
        StringBuilder csv = new StringBuilder();

        switch (type.toLowerCase()) {
            case "ar-aging" -> {
                ArAgingResponse aging = getArAging(LocalDate.now(clock), payerId);
                csv.append("Bucket Key,Label,Min Days,Max Days,Claim Count,Adjudicated Payer AR,Patient AR,Total Collectible AR,Disputed AR\n");
                for (AgingBucketItem b : aging.buckets()) {
                    csv.append(sanitize(b.bucketKey())).append(",")
                            .append(sanitize(b.label())).append(",")
                            .append(b.minDays()).append(",")
                            .append(b.maxDays() != null ? b.maxDays() : "").append(",")
                            .append(b.claimCount()).append(",")
                            .append(b.adjudicatedPayerAr()).append(",")
                            .append(b.patientAr()).append(",")
                            .append(b.totalCollectibleAr()).append(",")
                            .append(b.disputedAr()).append("\n");
                }
            }
            case "by-payer" -> {
                List<PayerPerformanceItem> payers = getArByPayer();
                csv.append("Payer Code,Payer Name,Total Claims,Total Billed,Net Payments,Verified Write-Offs,Adjudicated Payer AR,Patient AR,Disputed Exposure,Denial Rate,Avg Days\n");
                for (PayerPerformanceItem p : payers) {
                    csv.append(sanitize(p.payerCode())).append(",")
                            .append(sanitize(p.payerName())).append(",")
                            .append(p.totalClaims()).append(",")
                            .append(p.totalBilled()).append(",")
                            .append(p.netPayments()).append(",")
                            .append(p.verifiedWriteOffs()).append(",")
                            .append(p.adjudicatedPayerAr()).append(",")
                            .append(p.patientAr()).append(",")
                            .append(p.disputedExposure()).append(",")
                            .append(p.denialRate()).append("%,")
                            .append(String.format(Locale.US, "%.1f", p.avgProcessingDays())).append("\n");
                }
            }
            case "denials" -> {
                List<DenialReasonItem> reasons = getDenialsByReason();
                csv.append("CARC Code,Category,Payer,Reason Description,Claim Count,Total Amount,Percentage of Denials\n");
                for (DenialReasonItem r : reasons) {
                    csv.append(sanitize(r.code())).append(",")
                            .append(sanitize(r.categoryDescription())).append(",")
                            .append(sanitize(r.payerName())).append(",")
                            .append(sanitize(r.reason())).append(",")
                            .append(r.count()).append(",")
                            .append(r.totalAmount()).append(",")
                            .append(r.percentageOfTotal()).append("%\n");
                }
            }
            case "appeals" -> {
                AppealPerformanceResponse resp = getAppealPerformance();
                csv.append("Appeal Level,Description,Total Count,Overturned,Upheld,Pending\n");
                for (AppealLevelBreakdown l : resp.byLevel()) {
                    csv.append(l.level()).append(",")
                            .append(sanitize(l.levelDescription())).append(",")
                            .append(l.totalCount()).append(",")
                            .append(l.overturnedCount()).append(",")
                            .append(l.upheldCount()).append(",")
                            .append(l.pendingCount()).append("\n");
                }
            }
            case "priority-claims" -> {
                List<PriorityClaimItem> priorities = getPriorityClaims();
                csv.append("Priority,Claim Number,Payer,Status,Days Outstanding,Outstanding Amount,Category,Reason,Recommended Action\n");
                for (PriorityClaimItem p : priorities) {
                    csv.append(sanitize(p.priority())).append(",")
                            .append(sanitize(p.claimNumber())).append(",")
                            .append(sanitize(p.payerName())).append(",")
                            .append(sanitize(p.status().name())).append(",")
                            .append(p.daysOutstanding()).append(",")
                            .append(p.outstandingAmount()).append(",")
                            .append(sanitize(p.category())).append(",")
                            .append(sanitize(p.reason())).append(",")
                            .append(sanitize(p.recommendedAction())).append("\n");
                }
            }
            default -> throw new BusinessRuleException("Unsupported export type: '" + type + "'. Supported types: ar-aging, by-payer, denials, appeals, priority-claims.");
        }

        return csv.toString();
    }

    /**
     * Sanitizes CSV field values to prevent CSV injection (formula injection starting with =, +, -, @)
     * and applies RFC 4180 escaping (quotes).
     */
    private String sanitize(String value) {
        if (value == null) return "";
        String s = value.trim();
        // Prevent formula injection in spreadsheet applications (Excel, LibreOffice)
        if (s.startsWith("=") || s.startsWith("+") || s.startsWith("-") || s.startsWith("@")) {
            s = "'" + s;
        }
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private static class AgingAccumulator {
        String label;
        int minDays;
        Integer maxDays;
        int claimCount = 0;
        BigDecimal adjudicatedPayerAr = BigDecimal.ZERO;
        BigDecimal patientAr = BigDecimal.ZERO;
        BigDecimal totalCollectibleAr = BigDecimal.ZERO;
        BigDecimal disputedAr = BigDecimal.ZERO;

        AgingAccumulator(String label, int minDays, Integer maxDays) {
            this.label = label;
            this.minDays = minDays;
            this.maxDays = maxDays;
        }
    }
}
