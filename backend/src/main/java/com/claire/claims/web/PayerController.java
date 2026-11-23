package com.claire.claims.web;

import com.claire.claims.dto.ClaimDtos.PageResponse;
import com.claire.claims.dto.ReferenceDtos.*;
import com.claire.claims.service.ReferenceDataService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/payers")
public class PayerController {

    private final ReferenceDataService service;

    public PayerController(ReferenceDataService service) {
        this.service = service;
    }

    @GetMapping
    public List<PayerResponse> list() {
        return service.listPayers();
    }

    @GetMapping("/search")
    public PageResponse<PayerResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean active,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return service.searchPayers(q, active, pageable);
    }

    @GetMapping("/{id}")
    public PayerResponse get(@PathVariable Long id) {
        return service.getPayer(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PayerResponse> create(@Valid @RequestBody PayerRequest request) {
        PayerResponse created = service.createPayer(request);
        return ResponseEntity.created(URI.create("/api/payers/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public PayerResponse update(@PathVariable Long id, @Valid @RequestBody PayerRequest request) {
        return service.updatePayer(id, request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public PayerResponse setStatus(@PathVariable Long id, @RequestParam boolean active) {
        return service.setPayerStatus(id, active);
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasRole('ADMIN')")
    public PayerResponse archive(@PathVariable Long id) {
        return service.setPayerStatus(id, false);
    }

    @PostMapping("/{id}/reactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public PayerResponse reactivate(@PathVariable Long id) {
        return service.setPayerStatus(id, true);
    }
}
