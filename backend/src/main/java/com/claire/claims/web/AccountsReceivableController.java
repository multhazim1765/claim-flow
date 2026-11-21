package com.claire.claims.web;

import com.claire.claims.dto.AccountsReceivableDtos.AccountsReceivableSummaryResponse;
import com.claire.claims.dto.AccountsReceivableDtos.ClaimArBreakdownResponse;
import com.claire.claims.service.financial.AccountsReceivableService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AccountsReceivableController {

    private final AccountsReceivableService arService;

    public AccountsReceivableController(AccountsReceivableService arService) {
        this.arService = arService;
    }

    /**
     * Retrieves the high-integrity segmented Accounts Receivable summary across all claims.
     */
    @GetMapping("/api/financial/ar-summary")
    @PreAuthorize("hasAnyRole('ADMIN','BILLER','VIEWER')")
    public ResponseEntity<AccountsReceivableSummaryResponse> getArSummary() {
        AccountsReceivableSummaryResponse summary = arService.getArSummary();
        return ResponseEntity.ok(summary);
    }

    /**
     * Retrieves the detailed Accounts Receivable breakdown for a specific claim.
     */
    @GetMapping("/api/claims/{id}/ar-breakdown")
    @PreAuthorize("hasAnyRole('ADMIN','BILLER','VIEWER')")
    public ResponseEntity<ClaimArBreakdownResponse> getClaimArBreakdown(@PathVariable Long id) {
        ClaimArBreakdownResponse breakdown = arService.getClaimArBreakdown(id);
        return ResponseEntity.ok(breakdown);
    }
}

