package com.claire.claims.web;

import com.claire.claims.dto.PaymentPostingDtos.*;
import com.claire.claims.service.remittance.PaymentPostingService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class PaymentPostingController {

    private final PaymentPostingService postingService;

    public PaymentPostingController(PaymentPostingService postingService) {
        this.postingService = postingService;
    }

    /**
     * Posts a single validated, balanced payment voucher to its matched claim and ledger.
     */
    @PostMapping("/api/remittances/vouchers/{id}/post")
    @PreAuthorize("hasAnyRole('ADMIN','BILLER')")
    public ResponseEntity<VoucherPostingResponse> postVoucher(@PathVariable Long id) {
        VoucherPostingResponse response = postingService.postVoucher(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Atomically posts all validated vouchers in a remittance batch.
     */
    @PostMapping("/api/remittances/{id}/post")
    @PreAuthorize("hasAnyRole('ADMIN','BILLER')")
    public ResponseEntity<BatchPostingResponse> postBatch(@PathVariable Long id) {
        BatchPostingResponse response = postingService.postBatch(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves the chronological audit ledger of all payments and write-offs for a claim.
     */
    @GetMapping("/api/claims/{id}/ledger")
    @PreAuthorize("hasAnyRole('ADMIN','BILLER','VIEWER')")
    public ResponseEntity<List<PaymentLedgerResponse>> getClaimLedger(@PathVariable Long id) {
        List<PaymentLedgerResponse> response = postingService.getClaimLedger(id);
        return ResponseEntity.ok(response);
    }
}

