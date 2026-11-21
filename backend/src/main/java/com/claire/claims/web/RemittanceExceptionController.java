package com.claire.claims.web;

import com.claire.claims.dto.RemittanceExceptionDtos.ExceptionResolutionRequest;
import com.claire.claims.dto.RemittanceExceptionDtos.RemittanceExceptionResponse;
import com.claire.claims.service.remittance.RemittanceExceptionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class RemittanceExceptionController {

    private final RemittanceExceptionService exceptionService;

    public RemittanceExceptionController(RemittanceExceptionService exceptionService) {
        this.exceptionService = exceptionService;
    }

    /**
     * Retrieves a single remittance exception by ID.
     */
    @GetMapping("/api/remittances/exceptions/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','BILLER','VIEWER')")
    public ResponseEntity<RemittanceExceptionResponse> getException(@PathVariable Long id) {
        RemittanceExceptionResponse response = exceptionService.getException(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Resolves an open remittance exception with an action, optional matched claim, and notes.
     */
    @PostMapping("/api/remittances/exceptions/{id}/resolve")
    @PreAuthorize("hasAnyRole('ADMIN','BILLER')")
    public ResponseEntity<RemittanceExceptionResponse> resolveException(
            @PathVariable Long id,
            @Valid @RequestBody ExceptionResolutionRequest request) {
        RemittanceExceptionResponse response = exceptionService.resolveException(id, request);
        return ResponseEntity.ok(response);
    }
}
