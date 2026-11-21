package com.claire.claims.web;

import com.claire.claims.dto.PaymentReversalDtos.PaymentReversalRequest;
import com.claire.claims.dto.PaymentReversalDtos.PaymentReversalResponse;
import com.claire.claims.service.remittance.PaymentReversalService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentReversalController {

    private final PaymentReversalService reversalService;

    public PaymentReversalController(PaymentReversalService reversalService) {
        this.reversalService = reversalService;
    }

    /**
     * Executes an append-only reversal of a posted payer payment, restoring financial balances
     * and reopening claim status where appropriate.
     */
    @PostMapping("/api/claims/{id}/reversals")
    @PreAuthorize("hasAnyRole('ADMIN','BILLER')")
    public ResponseEntity<PaymentReversalResponse> reversePayment(
            @PathVariable Long id,
            @Valid @RequestBody PaymentReversalRequest request) {
        PaymentReversalResponse response = reversalService.reversePayment(id, request);
        return ResponseEntity.ok(response);
    }
}

