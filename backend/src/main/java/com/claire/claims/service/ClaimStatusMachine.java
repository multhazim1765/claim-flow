package com.claire.claims.service;

import com.claire.claims.common.ApiExceptions.BusinessRuleException;
import com.claire.claims.common.ApiExceptions.ConflictException;
import com.claire.claims.common.ApiExceptions.NotImplementedYetException;
import com.claire.claims.domain.Claim;
import com.claire.claims.domain.ClaimStatus;
import com.claire.claims.domain.ClaimStatusHistory;
import com.claire.claims.dto.ClaimDtos.TransitionRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The one place claim status changes. Every rule about what may follow what,
 * what a transition requires, and what it does to the money lives here.
 *
 * Controllers and the UI never decide a transition is legal on their own -
 * they ask this class, or read {@code /api/reference/status-transitions},
 * which is generated from the same enum.
 */
@Component
public class ClaimStatusMachine {

    /**
     * Validates and applies a transition, mutating the claim and returning the
     * audit row to persist. Throws rather than returning a failure code, because
     * an illegal transition is a client error, not a branch the caller handles.
     */
    public ClaimStatusHistory apply(Claim claim, TransitionRequest request, String actor) {
        ClaimStatus from = claim.getStatus();
        ClaimStatus to = request.targetStatus();

        if (from == to) {
            throw new ConflictException("Claim " + claim.getClaimNumber() + " is already " + from);
        }

        // Rule 12: Generic transition endpoints cannot bypass the formal appeal workflow.
        if (to == ClaimStatus.APPEALED) {
            throw new BusinessRuleException(
                    "Cannot transition directly to APPEALED via generic transition. File an appeal via POST /api/claims/{id}/appeal.");
        }
        if (from == ClaimStatus.APPEALED && to != ClaimStatus.VOID) {
            throw new BusinessRuleException(
                    "Cannot transition claim " + claim.getClaimNumber()
                    + " from APPEALED via generic transition. Record the appeal outcome via POST /api/appeals/{id}/outcome.");
        }

        if (!from.canTransitionTo(to)) {
            String allowed = from.allowedNext().isEmpty()
                    ? "none - " + from + " is a terminal state"
                    : from.allowedNext().stream().map(Enum::name).collect(Collectors.joining(", "));
            throw new ConflictException(
                    "Cannot move claim " + claim.getClaimNumber() + " from " + from + " to " + to
                    + ". Allowed from " + from + ": " + allowed + ".");
        }

        switch (to) {
            case SUBMITTED      -> onSubmit(claim);
            case REJECTED       -> requireReason(request, "A rejection must record the payer's reason");
            case DENIED         -> requireReason(request, "A denial must record the payer's reason");
            case VOID           -> requireReason(request, "Voiding a claim must record why");
            case DRAFT          -> onReopen(claim);
            case PAID           -> onPaid(claim, request);
            case PARTIALLY_PAID -> onPartiallyPaid(claim, request);
            default             -> { /* ACCEPTED and APPEALED carry no side effects */ }
        }

        claim.setStatus(to);
        return new ClaimStatusHistory(claim, from, to, request.reason(), actor);
    }

    // ----- transition side effects ---------------------------------------

    private void onSubmit(Claim claim) {
        if (claim.getLines().isEmpty()) {
            throw new BusinessRuleException("A claim cannot be submitted without at least one service line");
        }
        if (claim.getDiagnoses().isEmpty()) {
            throw new BusinessRuleException("A claim cannot be submitted without at least one diagnosis");
        }
        if (claim.getTotalCharge().signum() <= 0) {
            throw new BusinessRuleException("A claim cannot be submitted with a zero total charge");
        }
        if (claim.getPolicy() != null && !claim.getPolicy().isActiveOn(claim.getServiceDateFrom())) {
            throw new BusinessRuleException(
                    "Policy " + claim.getPolicy().getMemberId() + " is not active on the service date "
                    + claim.getServiceDateFrom() + ". Attach current coverage before submitting.");
        }
        claim.setSubmittedAt(OffsetDateTime.now());
    }

    /** REJECTED -> DRAFT: the claim goes back to the biller for correction. */
    private void onReopen(Claim claim) {
        claim.setSubmittedAt(null);
        claim.setAllowedAmount(BigDecimal.ZERO);
        claim.setPaidAmount(BigDecimal.ZERO);
        claim.setPatientResponsibility(BigDecimal.ZERO);
    }

    private void onPaid(Claim claim, TransitionRequest request) {
        BigDecimal allowed = resolveAllowed(claim, request);
        BigDecimal paid = require(request.paidAmount(), "paidAmount is required when marking a claim PAID");
        if (paid.compareTo(allowed) > 0) {
            throw new BusinessRuleException(
                    "Paid amount " + paid + " exceeds the allowed amount " + allowed);
        }
        claim.setAllowedAmount(scale(allowed));
        claim.setPaidAmount(scale(paid));
        claim.setPatientResponsibility(scale(allowed.subtract(paid).max(BigDecimal.ZERO)));
    }

    private void onPartiallyPaid(Claim claim, TransitionRequest request) {
        BigDecimal allowed = resolveAllowed(claim, request);
        BigDecimal paid = require(request.paidAmount(),
                "paidAmount is required when marking a claim PARTIALLY_PAID");
        if (paid.signum() <= 0) {
            throw new BusinessRuleException("A partial payment must be greater than zero");
        }
        if (paid.compareTo(allowed) >= 0) {
            throw new BusinessRuleException(
                    "Paid amount " + paid + " covers the full allowed amount " + allowed
                    + ". Use PAID instead of PARTIALLY_PAID.");
        }
        claim.setAllowedAmount(scale(allowed));
        claim.setPaidAmount(scale(paid));
        claim.setPatientResponsibility(scale(allowed.subtract(paid)));
    }

    // ----- helpers --------------------------------------------------------

    private BigDecimal resolveAllowed(Claim claim, TransitionRequest request) {
        if (request.allowedAmount() != null && request.allowedAmount().signum() > 0) {
            return request.allowedAmount();
        }
        if (claim.getAllowedAmount() != null && claim.getAllowedAmount().signum() > 0) {
            return claim.getAllowedAmount();
        }
        return claim.getTotalCharge();
    }

    private BigDecimal require(BigDecimal value, String message) {
        if (value == null) {
            throw new BusinessRuleException(message);
        }
        return value;
    }

    private void requireReason(TransitionRequest request, String message) {
        if (request.reason() == null || request.reason().isBlank()) {
            throw new BusinessRuleException(message);
        }
    }

    private BigDecimal scale(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Drives DENIED -> APPEALED as part of a formal appeal filing.
     * submittedAt and financial balances remain untouched.
     */
    public ClaimStatusHistory applyAppeal(Claim claim, String narrative, String actor) {
        ClaimStatus from = claim.getStatus();
        if (from != ClaimStatus.DENIED) {
            throw new ConflictException("Cannot appeal claim " + claim.getClaimNumber()
                    + " with status " + from + ". Only DENIED claims can be appealed.");
        }
        claim.setStatus(ClaimStatus.APPEALED);
        return new ClaimStatusHistory(claim, from, ClaimStatus.APPEALED, narrative, actor);
    }

    /**
     * Drives APPEALED -> ACCEPTED (overturned/partial) or APPEALED -> DENIED (upheld/withdrawn).
     * Financial balances remain untouched until remittance is received.
     */
    public ClaimStatusHistory applyAppealOutcome(Claim claim, ClaimStatus targetStatus, String reason, String actor) {
        ClaimStatus from = claim.getStatus();
        if (from != ClaimStatus.APPEALED) {
            throw new ConflictException("Claim " + claim.getClaimNumber() + " is " + from + ", not APPEALED.");
        }
        if (targetStatus != ClaimStatus.ACCEPTED && targetStatus != ClaimStatus.DENIED && targetStatus != ClaimStatus.VOID) {
            throw new ConflictException("Invalid appeal outcome target status: " + targetStatus);
        }
        claim.setStatus(targetStatus);
        return new ClaimStatusHistory(claim, from, targetStatus, reason, actor);
    }

    /**
     * Drives claim status transitions specifically originating from ERA 835 payment posting.
     * Enforces valid status progressions:
     * - SUBMITTED -> records two discrete transitions:
     *     1. SUBMITTED -> ACCEPTED ("Payer adjudication inferred via ERA 835 remittance check <checkNumber>")
     *     2. ACCEPTED -> PAID or PARTIALLY_PAID ("Payment of $<amount> posted from ERA 835 check <checkNumber>")
     * - ACCEPTED -> records ACCEPTED -> PAID or PARTIALLY_PAID
     * - PARTIALLY_PAID -> if target is PAID, records PARTIALLY_PAID -> PAID; if remaining PARTIALLY_PAID, records no transition (no-op prevented)
     * - Rejects DRAFT, VOID, REJECTED, and DENIED.
     * - Returns the list of generated audit transitions to persist.
     */
    public List<ClaimStatusHistory> applyPaymentPosting(Claim claim, ClaimStatus targetStatus,
                                                        BigDecimal paymentAmount, String checkNumber, String actor) {
        ClaimStatus from = claim.getStatus();
        if (from == ClaimStatus.DRAFT || from == ClaimStatus.VOID || from == ClaimStatus.REJECTED || from == ClaimStatus.DENIED) {
            throw new BusinessRuleException("Cannot post payment to claim " + claim.getClaimNumber() + " with status " + from);
        }
        if (from == ClaimStatus.PAID) {
            throw new ConflictException("Claim " + claim.getClaimNumber() + " is already PAID.");
        }
        if (targetStatus != ClaimStatus.PAID && targetStatus != ClaimStatus.PARTIALLY_PAID) {
            throw new BusinessRuleException("Invalid target status for payment posting: " + targetStatus);
        }

        List<ClaimStatusHistory> transitions = new ArrayList<>();
        String paymentReason = String.format("Payment of $%s posted from ERA 835 check %s",
                paymentAmount.setScale(2, RoundingMode.HALF_UP).toPlainString(), checkNumber);

        if (from == ClaimStatus.SUBMITTED) {
            // Step 1: Infer acceptance/adjudication from the ERA 835 remittance (not a separate 277CA)
            String step1Reason = String.format(
                    "Payer adjudication inferred via ERA 835 remittance check %s (inferred from ERA, not separate 277CA acknowledgment)",
                    checkNumber);
            claim.setStatus(ClaimStatus.ACCEPTED);
            transitions.add(new ClaimStatusHistory(claim, ClaimStatus.SUBMITTED, ClaimStatus.ACCEPTED, step1Reason, actor));

            // Step 2: Transition from ACCEPTED to target status
            claim.setStatus(targetStatus);
            transitions.add(new ClaimStatusHistory(claim, ClaimStatus.ACCEPTED, targetStatus, paymentReason, actor));
        } else if (from == ClaimStatus.ACCEPTED) {
            claim.setStatus(targetStatus);
            transitions.add(new ClaimStatusHistory(claim, ClaimStatus.ACCEPTED, targetStatus, paymentReason, actor));
        } else if (from == ClaimStatus.PARTIALLY_PAID) {
            if (targetStatus == ClaimStatus.PAID) {
                claim.setStatus(ClaimStatus.PAID);
                transitions.add(new ClaimStatusHistory(claim, ClaimStatus.PARTIALLY_PAID, ClaimStatus.PAID, paymentReason, actor));
            }
            // If targetStatus == PARTIALLY_PAID, claim status remains PARTIALLY_PAID without no-op transition history
        }

        return transitions;
    }
}
