package com.claire.claims.web;

import com.claire.claims.domain.DenialCategory;
import com.claire.claims.domain.DenialStatus;
import com.claire.claims.dto.ClaimDtos.PageResponse;
import com.claire.claims.dto.DenialDtos.*;
import com.claire.claims.service.DenialService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/denials")
public class DenialController {

    private final DenialService denialService;

    public DenialController(DenialService denialService) {
        this.denialService = denialService;
    }

    /**
     * Paginated and filterable denial worklist.
     * Accessible by ADMIN, BILLER, and VIEWER roles.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','BILLER','VIEWER')")
    public PageResponse<DenialWorklistItem> list(
            @RequestParam(required = false) Long payerId,
            @RequestParam(required = false) DenialCategory category,
            @RequestParam(required = false) DenialStatus status,
            @RequestParam(required = false) String urgency,
            @PageableDefault(size = 20, sort = "appealDeadline", direction = Sort.Direction.ASC) Pageable pageable) {
        return denialService.search(payerId, category, status, urgency, pageable);
    }

    /**
     * Summary metrics for open denials, deadlines, and categories.
     * Accessible by ADMIN, BILLER, and VIEWER roles.
     */
    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('ADMIN','BILLER','VIEWER')")
    public DenialSummaryResponse summary() {
        return denialService.getSummary();
    }

    /**
     * Single denial details by ID.
     * Accessible by ADMIN, BILLER, and VIEWER roles.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','BILLER','VIEWER')")
    public DenialResponse getById(@PathVariable Long id) {
        return denialService.getById(id);
    }

    /**
     * Records a new structured denial on an adjudicated claim.
     * Restricted to ADMIN and BILLER roles.
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','BILLER')")
    public ResponseEntity<DenialResponse> create(@Valid @RequestBody CreateDenialRequest request) {
        DenialResponse response = denialService.createDenial(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}

