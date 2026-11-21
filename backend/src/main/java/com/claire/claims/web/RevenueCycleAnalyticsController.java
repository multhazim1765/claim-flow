package com.claire.claims.web;

import com.claire.claims.dto.RevenueCycleAnalyticsDtos.*;
import com.claire.claims.service.financial.RevenueCycleAnalyticsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/api/analytics")
@PreAuthorize("hasAnyRole('ADMIN','BILLER','VIEWER')")
public class RevenueCycleAnalyticsController {

    private final RevenueCycleAnalyticsService analyticsService;

    public RevenueCycleAnalyticsController(RevenueCycleAnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    /**
     * Executive Overview KPIs across financial, operational, and RCM performance dimensions.
     */
    @GetMapping("/overview")
    public ResponseEntity<AnalyticsOverviewResponse> getOverview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long payerId,
            @RequestParam(required = false) Long providerId) {
        AnalyticsOverviewResponse response = analyticsService.getOverview(startDate, endDate, payerId, providerId);
        return ResponseEntity.ok(response);
    }

    /**
     * Accounts Receivable Aging breakdown across 5 standard aging intervals (0-30, 31-60, 61-90, 91-120, 120+).
     */
    @GetMapping({"/ar/aging", "/ar-aging"})
    public ResponseEntity<ArAgingResponse> getArAging(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate,
            @RequestParam(required = false) Long payerId) {
        ArAgingResponse response = analyticsService.getArAging(asOfDate, payerId);
        return ResponseEntity.ok(response);
    }

    /**
     * Payer performance metrics including billed, payments, reversals, AR, write-offs, and denial rates.
     */
    @GetMapping({"/ar/by-payer", "/by-payer"})
    public ResponseEntity<List<PayerPerformanceItem>> getArByPayer() {
        List<PayerPerformanceItem> response = analyticsService.getArByPayer();
        return ResponseEntity.ok(response);
    }

    /**
     * Claims and AR breakdown segmented by claim lifecycle status.
     */
    @GetMapping({"/ar/by-status", "/by-status"})
    public ResponseEntity<List<StatusBreakdownItem>> getArByStatus() {
        List<StatusBreakdownItem> response = analyticsService.getArByStatus();
        return ResponseEntity.ok(response);
    }

    /**
     * Chronological monthly payment and contractual write-off trends from the immutable ledger.
     */
    @GetMapping({"/payments/trends", "/payment-trends"})
    public ResponseEntity<PaymentTrendsResponse> getPaymentTrends(
            @RequestParam(required = false) Integer months) {
        PaymentTrendsResponse response = analyticsService.getPaymentTrends(months);
        return ResponseEntity.ok(response);
    }

    /**
     * Monthly denial volume and appeal outcome trends.
     */
    @GetMapping({"/denials/trends", "/denial-trends"})
    public ResponseEntity<DenialTrendsResponse> getDenialTrends() {
        DenialTrendsResponse response = analyticsService.getDenialTrends();
        return ResponseEntity.ok(response);
    }

    /**
     * Root-cause denial reason analysis grouped by CARC code and category.
     */
    @GetMapping({"/denials/by-reason", "/denial-reasons"})
    public ResponseEntity<List<DenialReasonItem>> getDenialsByReason() {
        List<DenialReasonItem> response = analyticsService.getDenialsByReason();
        return ResponseEntity.ok(response);
    }

    /**
     * Multi-level appeal turnaround times, overturn rates, and upcoming response deadlines.
     */
    @GetMapping({"/appeals/performance", "/appeals"})
    public ResponseEntity<AppealPerformanceResponse> getAppealPerformance() {
        AppealPerformanceResponse response = analyticsService.getAppealPerformance();
        return ResponseEntity.ok(response);
    }

    /**
     * Real-time 270/271 eligibility verification throughput, active coverage, and cache efficiency.
     */
    @GetMapping({"/eligibility/performance", "/eligibility"})
    public ResponseEntity<EligibilityPerformanceResponse> getEligibilityPerformance() {
        EligibilityPerformanceResponse response = analyticsService.getEligibilityPerformance();
        return ResponseEntity.ok(response);
    }

    /**
     * Deterministic, explainable priority claims worklist and smart revenue cycle action items.
     */
    @GetMapping("/priority-claims")
    public ResponseEntity<List<PriorityClaimItem>> getPriorityClaims() {
        List<PriorityClaimItem> response = analyticsService.getPriorityClaims();
        return ResponseEntity.ok(response);
    }

    /**
     * RFC 4180 compliant CSV export with formula injection sanitization.
     */
    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<byte[]> exportAnalytics(
            @RequestParam String type,
            @RequestParam(required = false) Long payerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        String csvContent = analyticsService.exportCsv(type, payerId, startDate, endDate);
        byte[] bytes = csvContent.getBytes(StandardCharsets.UTF_8);

        String filename = "claimflow-" + type.toLowerCase() + "-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".csv";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(bytes);
    }
}

