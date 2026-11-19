package com.claire.claims.service;

import com.claire.claims.common.ApiExceptions.BusinessRuleException;
import com.claire.claims.common.ApiExceptions.ConflictException;
import com.claire.claims.common.ApiExceptions.NotFoundException;
import com.claire.claims.domain.*;
import com.claire.claims.dto.ClaimDtos.PageResponse;
import com.claire.claims.dto.DenialDtos.*;
import com.claire.claims.repository.ClaimDenialRepository;
import com.claire.claims.repository.ClaimRepository;
import com.claire.claims.security.CurrentUserProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class DenialService {

    private final ClaimDenialRepository claimDenials;
    private final ClaimRepository claims;
    private final CurrentUserProvider currentUser;

    public DenialService(ClaimDenialRepository claimDenials,
                         ClaimRepository claims,
                         CurrentUserProvider currentUser) {
        this.claimDenials = claimDenials;
        this.claims = claims;
        this.currentUser = currentUser;
    }

    // =====================================================================
    // Queries
    // =====================================================================

    @Transactional(readOnly = true)
    public PageResponse<DenialWorklistItem> search(Long payerId,
                                                   DenialCategory category,
                                                   DenialStatus status,
                                                   String urgency,
                                                   Pageable pageable) {
        LocalDate today = LocalDate.now();

        Specification<ClaimDenial> spec = (root, query, cb) -> cb.conjunction();

        if (payerId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("payer").get("id"), payerId));
        }

        if (category != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("denialCategory"), category));
        }

        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }

        if (urgency != null && !urgency.isBlank()) {
            String u = urgency.trim().toUpperCase();
            switch (u) {
                case "OVERDUE" ->
                    spec = spec.and((root, query, cb) -> cb.lessThan(root.get("appealDeadline"), today));
                case "CRITICAL" ->
                    spec = spec.and((root, query, cb) -> cb.between(root.get("appealDeadline"), today, today.plusDays(7)));
                case "WARNING" ->
                    spec = spec.and((root, query, cb) -> cb.between(root.get("appealDeadline"), today.plusDays(8), today.plusDays(30)));
                case "NORMAL" ->
                    spec = spec.and((root, query, cb) -> cb.greaterThan(root.get("appealDeadline"), today.plusDays(30)));
                default ->
                    throw new BusinessRuleException("Unknown urgency filter: " + urgency + ". Allowed: OVERDUE, CRITICAL, WARNING, NORMAL.");
            }
        }

        Page<ClaimDenial> page = claimDenials.findAll(spec, pageable);
        List<DenialWorklistItem> items = page.getContent().stream()
                .map(d -> toWorklistItem(d, today))
                .toList();

        return new PageResponse<>(
                items,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast()
        );
    }

    @Transactional(readOnly = true)
    public DenialSummaryResponse getSummary() {
        LocalDate today = LocalDate.now();

        Specification<ClaimDenial> openSpec = (root, query, cb) -> cb.equal(root.get("status"), DenialStatus.OPEN);
        List<ClaimDenial> openDenials = claimDenials.findAll(openSpec);

        long totalOpen = openDenials.size();
        BigDecimal totalOpenAmount = BigDecimal.ZERO;

        long critical = 0;
        long warning = 0;
        long normal = 0;
        long overdue = 0;

        Map<String, CategoryBreakdown> byCategory = new LinkedHashMap<>();
        for (DenialCategory cat : DenialCategory.values()) {
            byCategory.put(cat.name(), new CategoryBreakdown(cat, cat.getDescription(), 0, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)));
        }

        for (ClaimDenial d : openDenials) {
            BigDecimal amt = d.getAmount() != null ? d.getAmount() : BigDecimal.ZERO;
            totalOpenAmount = totalOpenAmount.add(amt);

            long daysRemaining = ChronoUnit.DAYS.between(today, d.getAppealDeadline());
            if (daysRemaining < 0) {
                overdue++;
            } else if (daysRemaining <= 7) {
                critical++;
            } else if (daysRemaining <= 30) {
                warning++;
            } else {
                normal++;
            }

            DenialCategory cat = d.getDenialCategory();
            if (cat != null) {
                CategoryBreakdown prev = byCategory.get(cat.name());
                long newCount = prev.count() + 1;
                BigDecimal newAmt = prev.amount().add(amt);
                byCategory.put(cat.name(), new CategoryBreakdown(cat, cat.getDescription(), newCount, newAmt.setScale(2, RoundingMode.HALF_UP)));
            }
        }

        return new DenialSummaryResponse(
                totalOpen,
                totalOpenAmount.setScale(2, RoundingMode.HALF_UP),
                critical,
                warning,
                normal,
                overdue,
                byCategory
        );
    }

    @Transactional(readOnly = true)
    public DenialResponse getById(Long id) {
        ClaimDenial denial = claimDenials.findWithReferencesById(id)
                .orElseThrow(() -> new NotFoundException("ClaimDenial", id));
        return toResponse(denial, LocalDate.now());
    }

    // =====================================================================
    // Mutations
    // =====================================================================

    @Transactional
    public DenialResponse createDenial(CreateDenialRequest request) {
        Claim claim = claims.findWithReferencesById(request.claimId())
                .orElseThrow(() -> new NotFoundException("Claim", request.claimId()));

        if (claim.getStatus() != ClaimStatus.DENIED) {
            throw new ConflictException(
                    "Cannot record denial for claim " + claim.getClaimNumber()
                    + " with status " + claim.getStatus() + ". Claim must be in DENIED status.");
        }

        if (request.payerId() != null && !claim.getPayer().getId().equals(request.payerId())) {
            throw new BusinessRuleException(
                    "Claim " + claim.getClaimNumber() + " belongs to payer "
                    + claim.getPayer().getName() + " (ID: " + claim.getPayer().getId()
                    + "), not payer ID " + request.payerId());
        }

        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessRuleException("Denial amount must be non-negative");
        }

        if (request.amount().compareTo(claim.getTotalCharge()) > 0) {
            throw new BusinessRuleException(
                    "Denial amount " + request.amount() + " cannot exceed claim total charge "
                    + claim.getTotalCharge());
        }

        LocalDate today = LocalDate.now();
        if (request.denialDate().isAfter(today)) {
            throw new BusinessRuleException("Denial date cannot be in the future");
        }

        if (request.appealDeadline().isBefore(request.denialDate())) {
            throw new BusinessRuleException("Appeal deadline cannot be before the denial date");
        }

        boolean activeExists = claimDenials.existsByClaimIdAndStatusIn(
                claim.getId(),
                List.of(DenialStatus.OPEN, DenialStatus.APPEALED)
        );
        if (activeExists) {
            throw new ConflictException(
                    "An active denial record already exists for claim " + claim.getClaimNumber());
        }

        String username = currentUser.username();
        ClaimDenial denial = new ClaimDenial(
                claim,
                claim.getPayer(),
                request.denialCode().trim().toUpperCase(),
                request.remittanceCode() != null ? request.remittanceCode().trim().toUpperCase() : null,
                request.denialCategory(),
                request.reason().trim(),
                request.denialDate(),
                request.appealDeadline(),
                request.amount().setScale(2, RoundingMode.HALF_UP),
                username
        );

        ClaimDenial saved = claimDenials.save(denial);
        return toResponse(saved, today);
    }

    // =====================================================================
    // Mapping Helpers
    // =====================================================================

    private DenialWorklistItem toWorklistItem(ClaimDenial d, LocalDate today) {
        long daysRemaining = ChronoUnit.DAYS.between(today, d.getAppealDeadline());
        String urgency;
        if (daysRemaining < 0) {
            urgency = "OVERDUE";
        } else if (daysRemaining <= 7) {
            urgency = "CRITICAL";
        } else if (daysRemaining <= 30) {
            urgency = "WARNING";
        } else {
            urgency = "NORMAL";
        }

        String patientName = d.getClaim() != null && d.getClaim().getPatient() != null
                ? d.getClaim().getPatient().getDisplayName()
                : "Unknown";

        String patientMrn = d.getClaim() != null && d.getClaim().getPatient() != null
                ? d.getClaim().getPatient().getMrn()
                : "";

        return new DenialWorklistItem(
                d.getId(),
                d.getClaim().getId(),
                d.getClaim().getClaimNumber(),
                patientName,
                patientMrn,
                d.getPayer().getId(),
                d.getPayer().getName(),
                d.getDenialCode(),
                d.getRemittanceCode(),
                d.getDenialCategory(),
                d.getReason(),
                d.getDenialDate(),
                d.getAppealDeadline(),
                daysRemaining,
                urgency,
                d.getAmount().setScale(2, RoundingMode.HALF_UP),
                d.getStatus(),
                d.getCreatedBy(),
                d.getCreatedAt()
        );
    }

    private DenialResponse toResponse(ClaimDenial d, LocalDate today) {
        long daysRemaining = ChronoUnit.DAYS.between(today, d.getAppealDeadline());
        String urgency;
        if (daysRemaining < 0) {
            urgency = "OVERDUE";
        } else if (daysRemaining <= 7) {
            urgency = "CRITICAL";
        } else if (daysRemaining <= 30) {
            urgency = "WARNING";
        } else {
            urgency = "NORMAL";
        }

        return new DenialResponse(
                d.getId(),
                d.getClaim().getId(),
                d.getClaim().getClaimNumber(),
                d.getPayer().getId(),
                d.getPayer().getName(),
                d.getDenialCode(),
                d.getRemittanceCode(),
                d.getDenialCategory(),
                d.getReason(),
                d.getDenialDate(),
                d.getAppealDeadline(),
                daysRemaining,
                urgency,
                d.getAmount().setScale(2, RoundingMode.HALF_UP),
                d.getStatus(),
                d.getCreatedBy(),
                d.getCreatedAt()
        );
    }
}

