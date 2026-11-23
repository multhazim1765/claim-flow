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
@RequestMapping("/api/providers")
public class ProviderController {

    private final ReferenceDataService service;

    public ProviderController(ReferenceDataService service) {
        this.service = service;
    }

    @GetMapping
    public List<ProviderResponse> list() {
        return service.listProviders();
    }

    @GetMapping("/search")
    public PageResponse<ProviderResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean active,
            @PageableDefault(size = 20, sort = "lastName") Pageable pageable) {
        return service.searchProviders(q, active, pageable);
    }

    @GetMapping("/{id}")
    public ProviderResponse get(@PathVariable Long id) {
        return service.getProvider(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProviderResponse> create(@Valid @RequestBody ProviderRequest request) {
        ProviderResponse created = service.createProvider(request);
        return ResponseEntity.created(URI.create("/api/providers/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ProviderResponse update(@PathVariable Long id, @Valid @RequestBody ProviderRequest request) {
        return service.updateProvider(id, request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ProviderResponse setStatus(@PathVariable Long id, @RequestParam boolean active) {
        return service.setProviderStatus(id, active);
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasRole('ADMIN')")
    public ProviderResponse archive(@PathVariable Long id) {
        return service.setProviderStatus(id, false);
    }

    @PostMapping("/{id}/reactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ProviderResponse reactivate(@PathVariable Long id) {
        return service.setProviderStatus(id, true);
    }
}
