package com.claire.claims.service;

import com.claire.claims.common.ApiExceptions.BusinessRuleException;
import com.claire.claims.common.ApiExceptions.ConflictException;
import com.claire.claims.common.ApiExceptions.NotFoundException;
import com.claire.claims.domain.*;
import com.claire.claims.dto.AppealDtos.*;
import com.claire.claims.repository.ClaimAppealRepository;
import com.claire.claims.repository.ClaimDenialRepository;
import com.claire.claims.repository.ClaimRepository;
import com.claire.claims.repository.ClaimStatusHistoryRepository;
import com.claire.claims.security.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Service
public class AppealService {

    private final ClaimAppealRepository appeals;
    private final ClaimRepository claims;
    private final ClaimDenialRepository denials;
    private final ClaimStatusHistoryRepository statusHistory;
    private final ClaimStatusMachine statusMachine;
    private final CurrentUserProvider currentUser;

    public AppealService(ClaimAppealRepository appeals,
                         ClaimRepository claims,
                         ClaimDenialRepository denials,
                         ClaimStatusHistoryRepository statusHistory,
                         ClaimStatusMachine statusMachine,
                         CurrentUserProvider currentUser) {
        this.appeals = appeals;
        this.claims = claims;
        this.denials = denials;
        this.statusHistory = statusHistory;
        this.statusMachine = statusMachine;
        this.currentUser = currentUser;
    }

    // =====================================================================
    // Queries
    // =====================================================================

    @Transactional(readOnly = true)
    public List<AppealResponse> listAppeals(Long claimId) {
        if (!claims.existsById(claimId)) {
            throw new NotFoundException("Claim", claimId);
        }
        return appeals.findByClaimIdOrderByLevelAsc(claimId).stream()
                .map(a -> toResponse(a, a.getClaim().getStatus()))
                .toList();
    }

    @Transactional(readOnly = true)
    public AppealResponse getById(Long id) {
        ClaimAppeal appeal = appeals.findWithReferencesById(id)
                .orElseThrow(() -> new NotFoundException("ClaimAppeal", id));
        return toResponse(appeal, appeal.getClaim().getStatus());
    }

    // =====================================================================
    // Mutations
    // =====================================================================

    /**
     * Files a formal multi-level appeal against a denied claim.
     * Transactionally creates the ClaimAppeal, drives DENIED -> APPEALED,
     * updates the denial status, and appends to the immutable audit history.
     */
    @Transactional
    public AppealResponse fileAppeal(Long claimId, FileAppealRequest request) {
        Claim claim = claims.findWithReferencesById(claimId)
                .orElseThrow(() -> new NotFoundException("Claim", claimId));

        // Rule 1: Only DENIED claims can be appealed
        if (claim.getStatus() != ClaimStatus.DENIED) {
            throw new ConflictException(
                    "Only DENIED claims can be appealed. Claim " + claim.getClaimNumber()
                    + " is currently in status " + claim.getStatus() + ".");
        }

        // Rule 1 (cont): Must match an active OPEN denial record
        ClaimDenial denial;
        if (request.denialId() != null) {
            denial = denials.findById(request.denialId())
                    .orElseThrow(() -> new NotFoundException("ClaimDenial", request.denialId()));
            if (!denial.getClaim().getId().equals(claim.getId())) {
                throw new BusinessRuleException(
                        "Denial ID " + denial.getId() + " does not belong to claim " + claim.getClaimNumber());
            }
            if (denial.getStatus() != DenialStatus.OPEN) {
                throw new ConflictException(
                        "Denial ID " + denial.getId() + " is currently " + denial.getStatus()
                        + ". Only OPEN denials can be appealed.");
            }
        } else {
            List<ClaimDenial> openDenials = denials.findByClaimIdOrderByCreatedAtDesc(claim.getId())
                    .stream()
                    .filter(d -> d.getStatus() == DenialStatus.OPEN)
                    .toList();
            if (openDenials.isEmpty()) {
                throw new ConflictException(
                        "Claim " + claim.getClaimNumber() + " has no active OPEN denial record available for appeal.");
            }
            denial = openDenials.get(0);
        }

        // Rule 2: Rejection after denial appeal deadline
        LocalDate today = LocalDate.now();
        if (today.isAfter(denial.getAppealDeadline())) {
            throw new BusinessRuleException(
                    "Cannot file appeal: denial appeal deadline " + denial.getAppealDeadline()
                    + " expired on " + today + ".");
        }

        // Rule 4: Prevent duplicate pending appeals
        if (appeals.existsByClaimIdAndOutcomeIsNull(claim.getId())) {
            throw new ConflictException(
                    "Claim " + claim.getClaimNumber() + " already has a pending appeal awaiting decision.");
        }

        // Rule 3: Enforce appeal levels 1 to 3
        List<ClaimAppeal> previousAppeals = appeals.findByClaimIdOrderByLevelAsc(claim.getId());
        int level = 1;
        if (!previousAppeals.isEmpty()) {
            ClaimAppeal lastAppeal = previousAppeals.get(previousAppeals.size() - 1);
            if (lastAppeal.getOutcome() == null) {
                throw new ConflictException(
                        "Cannot escalate appeal: Level " + lastAppeal.getLevel() + " is still pending decision.");
            }
            if (lastAppeal.getOutcome() != AppealOutcome.UPHELD) {
                throw new ConflictException(
                        "Cannot escalate appeal: previous Level " + lastAppeal.getLevel()
                        + " outcome was " + lastAppeal.getOutcome() + ", not UPHELD.");
            }
            level = lastAppeal.getLevel() + 1;
            if (level > 3) {
                throw new ConflictException(
                        "Claim " + claim.getClaimNumber()
                        + " has reached the maximum allowed appeal tier (Level 3: External Review). No further appeals may be filed.");
            }
        }

        int deadlineDays = (request.responseDeadlineDays() != null && request.responseDeadlineDays() > 0)
                ? request.responseDeadlineDays()
                : 60;
        LocalDate responseDeadline = today.plusDays(deadlineDays);

        String actor = currentUser.username();
        ClaimAppeal appeal = new ClaimAppeal(
                claim,
                denial,
                level,
                today,
                responseDeadline,
                request.narrative().trim(),
                actor
        );
        ClaimAppeal savedAppeal = appeals.save(appeal);

        // Update denial status to APPEALED
        denial.setStatus(DenialStatus.APPEALED);
        denials.save(denial);

        // Rule 5, 6, 10: Apply transition to APPEALED; submittedAt remains untouched
        ClaimStatusHistory historyEntry = statusMachine.applyAppeal(
                claim,
                "Level " + level + " appeal filed: " + request.narrative().trim(),
                actor
        );
        statusHistory.save(historyEntry);
        claims.save(claim);

        return toResponse(savedAppeal, claim.getStatus());
    }

    /**
     * Records the payer's decision on a pending appeal.
     * OVERTURNED / PARTIAL -> claim moves to ACCEPTED (awaiting remittance).
     * UPHELD / WITHDRAWN   -> claim moves to DENIED.
     * Financial balances (paidAmount, allowedAmount) remain untouched until remittance is received.
     */
    @Transactional
    public AppealResponse recordOutcome(Long appealId, RecordOutcomeRequest request) {
        ClaimAppeal appeal = appeals.findWithReferencesById(appealId)
                .orElseThrow(() -> new NotFoundException("ClaimAppeal", appealId));

        if (appeal.getOutcome() != null) {
            throw new ConflictException(
                    "Appeal ID " + appealId + " has already been decided as "
                    + appeal.getOutcome() + " on " + appeal.getDecidedOn() + ".");
        }

        LocalDate today = LocalDate.now();
        if (request.decidedOn().isAfter(today)) {
            throw new BusinessRuleException("Decided date cannot be in the future");
        }
        if (request.decidedOn().isBefore(appeal.getFiledOn())) {
            throw new BusinessRuleException(
                    "Decided date " + request.decidedOn() + " cannot be before appeal filing date " + appeal.getFiledOn());
        }

        BigDecimal recovered = request.recoveredAmount() != null
                ? request.recoveredAmount().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        if (recovered.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessRuleException("Recovered amount cannot be negative");
        }

        appeal.setOutcome(request.outcome());
        appeal.setDecidedOn(request.decidedOn());
        appeal.setRecoveredAmount(recovered);
        appeal.setDecisionNotes(request.decisionNotes() != null ? request.decisionNotes().trim() : null);
        ClaimAppeal savedAppeal = appeals.save(appeal);

        Claim claim = appeal.getClaim();
        ClaimDenial denial = appeal.getDenial();
        String actor = currentUser.username();

        ClaimStatus targetStatus;
        String reason;

        switch (request.outcome()) {
            case OVERTURNED -> {
                targetStatus = ClaimStatus.ACCEPTED;
                reason = "Appeal Level " + appeal.getLevel() + " OVERTURNED in full by payer. Claim returned to adjudication awaiting remittance.";
                if (denial != null) {
                    denial.setStatus(DenialStatus.RESOLVED);
                    denials.save(denial);
                }
            }
            case PARTIAL -> {
                targetStatus = ClaimStatus.ACCEPTED;
                reason = "Appeal Level " + appeal.getLevel() + " PARTIALLY approved by payer (recovered: " + recovered + "). Claim returned to adjudication awaiting remittance.";
                if (denial != null) {
                    denial.setStatus(DenialStatus.RESOLVED);
                    denials.save(denial);
                }
            }
            case UPHELD -> {
                targetStatus = ClaimStatus.DENIED;
                reason = "Appeal Level " + appeal.getLevel() + " UPHELD by payer.";
                if (denial != null) {
                    if (appeal.getLevel() < 3) {
                        denial.setStatus(DenialStatus.OPEN);
                    } else {
                        denial.setStatus(DenialStatus.WRITTEN_OFF);
                    }
                    denials.save(denial);
                }
            }
            case WITHDRAWN -> {
                targetStatus = ClaimStatus.DENIED;
                reason = "Appeal Level " + appeal.getLevel() + " WITHDRAWN by provider.";
                if (denial != null) {
                    denial.setStatus(DenialStatus.WRITTEN_OFF);
                    denials.save(denial);
                }
            }
            default -> throw new BusinessRuleException("Unsupported outcome: " + request.outcome());
        }

        // Rule 7, 8, 9, 13: statusMachine handles status transition; balances remain untouched
        ClaimStatusHistory historyEntry = statusMachine.applyAppealOutcome(claim, targetStatus, reason, actor);
        statusHistory.save(historyEntry);
        claims.save(claim);

        return toResponse(savedAppeal, claim.getStatus());
    }

    // =====================================================================
    // Mapping Helpers
    // =====================================================================

    private AppealResponse toResponse(ClaimAppeal a, ClaimStatus claimStatus) {
        String levelDesc = switch (a.getLevel()) {
            case 1 -> "Level 1: Reconsideration";
            case 2 -> "Level 2: Formal Appeal";
            case 3 -> "Level 3: External Review";
            default -> "Level " + a.getLevel();
        };

        Long denialId = a.getDenial() != null ? a.getDenial().getId() : null;
        String denialCode = a.getDenial() != null ? a.getDenial().getDenialCode() : null;

        BigDecimal recovered = a.getRecoveredAmount() != null
                ? a.getRecoveredAmount().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        return new AppealResponse(
                a.getId(),
                a.getClaim().getId(),
                a.getClaim().getClaimNumber(),
                denialId,
                denialCode,
                a.getLevel(),
                levelDesc,
                a.getFiledOn(),
                a.getDeadline(),
                a.getNarrative(),
                a.getOutcome(),
                a.getDecidedOn(),
                recovered,
                a.getDecisionNotes(),
                claimStatus,
                a.getFiledBy(),
                a.getCreatedAt()
        );
    }
}

