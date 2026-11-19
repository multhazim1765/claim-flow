package com.claire.claims.service.remittance;

import com.claire.claims.common.ApiExceptions.BusinessRuleException;
import com.claire.claims.common.ApiExceptions.ConflictException;
import com.claire.claims.common.ApiExceptions.NotFoundException;
import com.claire.claims.domain.*;
import com.claire.claims.dto.PaymentPostingDtos.*;
import com.claire.claims.repository.*;
import com.claire.claims.security.CurrentUserProvider;
import com.claire.claims.service.ClaimStatusMachine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class PaymentPostingService {

    private final RemittanceClaimVoucherRepository vouchers;
    private final RemittanceBatchRepository batches;
    private final PaymentLedgerRepository ledger;
    private final ClaimRepository claims;
    private final ClaimStatusHistoryRepository statusHistories;
    private final ClaimStatusMachine statusMachine;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    @Autowired
    public PaymentPostingService(RemittanceClaimVoucherRepository vouchers,
                                 RemittanceBatchRepository batches,
                                 PaymentLedgerRepository ledger,
                                 ClaimRepository claims,
                                 ClaimStatusHistoryRepository statusHistories,
                                 ClaimStatusMachine statusMachine,
                                 CurrentUserProvider currentUserProvider) {
        this(vouchers, batches, ledger, claims, statusHistories, statusMachine, currentUserProvider, Clock.systemDefaultZone());
    }

    public PaymentPostingService(RemittanceClaimVoucherRepository vouchers,
                                 RemittanceBatchRepository batches,
                                 PaymentLedgerRepository ledger,
                                 ClaimRepository claims,
                                 ClaimStatusHistoryRepository statusHistories,
                                 ClaimStatusMachine statusMachine,
                                 CurrentUserProvider currentUserProvider,
                                 Clock clock) {
        this.vouchers = vouchers;
        this.batches = batches;
        this.ledger = ledger;
        this.claims = claims;
        this.statusHistories = statusHistories;
        this.statusMachine = statusMachine;
        this.currentUserProvider = currentUserProvider;
        this.clock = clock;
    }

    /**
     * Posts a single validated, balanced remittance voucher against its matched claim.
     */
    @Transactional
    public VoucherPostingResponse postVoucher(Long voucherId) {
        RemittanceClaimVoucher voucher = vouchers.findById(voucherId)
                .orElseThrow(() -> new NotFoundException("RemittanceClaimVoucher", voucherId));

        String actor = currentUserProvider.username();
        return postSingleVoucher(voucher, actor);
    }

    /**
     * Atomically posts all vouchers within a validated remittance batch.
     * Rollback is all-or-nothing: if any voucher fails validation, no financial updates occur.
     */
    @Transactional
    public BatchPostingResponse postBatch(Long remittanceId) {
        RemittanceBatch batch = batches.findById(remittanceId)
                .orElseThrow(() -> new NotFoundException("RemittanceBatch", remittanceId));

        if (batch.getStatus() == RemittanceBatchStatus.POSTED) {
            throw new ConflictException("Remittance batch " + batch.getBatchControlNumber() + " is already POSTED.");
        }
        if (batch.getStatus() != RemittanceBatchStatus.VALIDATED) {
            throw new BusinessRuleException("Cannot post remittance batch with status " + batch.getStatus()
                    + ". Only batches in VALIDATED status can be posted.");
        }

        List<RemittanceClaimVoucher> vList = vouchers.findByRemittanceIdOrderBySequenceNoAsc(remittanceId);
        if (vList.isEmpty()) {
            throw new BusinessRuleException("Remittance batch " + batch.getBatchControlNumber() + " has no vouchers to post.");
        }

        // STEP 1: Pre-validate all vouchers before any mutations (Atomicity check)
        for (RemittanceClaimVoucher v : vList) {
            validateVoucherForPosting(v);
        }

        // STEP 2: Sort vouchers deterministically by claim ID to prevent concurrent deadlock
        List<RemittanceClaimVoucher> sortedVouchers = new ArrayList<>(vList);
        sortedVouchers.sort(Comparator.comparing(v -> v.getClaim().getId()));

        String actor = currentUserProvider.username();
        List<VoucherPostingResponse> responses = new ArrayList<>();

        for (RemittanceClaimVoucher v : sortedVouchers) {
            responses.add(postSingleVoucher(v, actor));
        }

        batch.setStatus(RemittanceBatchStatus.POSTED);
        batches.save(batch);

        boolean hasFutureDated = responses.stream().anyMatch(VoucherPostingResponse::isFutureDated);
        String batchSettlementNote = hasFutureDated ? "Pending bank cash settlement" : null;

        return new BatchPostingResponse(
                batch.getId(),
                batch.getBatchControlNumber(),
                vList.size(),
                responses.size(),
                batch.getStatus(),
                hasFutureDated,
                batchSettlementNote,
                OffsetDateTime.now(),
                responses
        );
    }

    /**
     * Returns the complete chronological payment ledger history for a claim.
     */
    @Transactional(readOnly = true)
    public List<PaymentLedgerResponse> getClaimLedger(Long claimId) {
        if (!claims.existsById(claimId)) {
            throw new NotFoundException("Claim", claimId);
        }

        return ledger.findByClaimIdOrderByPostedAtAsc(claimId).stream()
                .map(this::toLedgerResponse)
                .toList();
    }

    // =====================================================================
    // Internal Posting Logic
    // =====================================================================

    private VoucherPostingResponse postSingleVoucher(RemittanceClaimVoucher voucher, String actor) {
        validateVoucherForPosting(voucher);

        Claim claim = voucher.getClaim();
        RemittanceBatch batch = voucher.getRemittance();
        ClaimStatus prevStatus = claim.getStatus();

        // 1. Calculate Target Claim Status based on residual patient responsibility
        ClaimStatus targetStatus = (voucher.getPatientResponsibility().signum() == 0)
                ? ClaimStatus.PAID
                : ClaimStatus.PARTIALLY_PAID;

        // Future-dated check date detection
        LocalDate today = LocalDate.now(clock);
        boolean isFutureDated = batch.getCheckDate() != null && batch.getCheckDate().isAfter(today);
        String settlementNote = isFutureDated ? "Pending bank cash settlement" : null;

        // 2. Drive State Machine transition
        List<ClaimStatusHistory> transitions = statusMachine.applyPaymentPosting(
                claim, targetStatus, voucher.getPaidAmount(), batch.getCheckNumber(), actor);
        if (!transitions.isEmpty()) {
            statusHistories.saveAll(transitions);
        }

        // 3. Atomically update claim financial fields preserving previous cumulative totals
        BigDecimal previousPaid = claim.getPaidAmount() != null ? claim.getPaidAmount() : BigDecimal.ZERO;
        BigDecimal newCumulativePaid = previousPaid.add(voucher.getPaidAmount()).setScale(2, RoundingMode.HALF_UP);
        claim.setPaidAmount(newCumulativePaid);

        claim.setAllowedAmount(voucher.getAllowedAmount().setScale(2, RoundingMode.HALF_UP));
        claim.setPatientResponsibility(voucher.getPatientResponsibility().setScale(2, RoundingMode.HALF_UP));

        BigDecimal previousCo = claim.getContractualAdjustment() != null ? claim.getContractualAdjustment() : BigDecimal.ZERO;
        BigDecimal newCumulativeCo = previousCo.add(voucher.getContractualAdj()).setScale(2, RoundingMode.HALF_UP);
        claim.setContractualAdjustment(newCumulativeCo);

        claims.save(claim);

        // 4. Append Immutable Ledger Entry for Payer Payment
        String auditReason = String.format("Payment of $%s posted from ERA 835 check %s",
                voucher.getPaidAmount().toPlainString(), batch.getCheckNumber());
        String paymentNotes = isFutureDated ? auditReason + " - Pending bank cash settlement" : auditReason;

        PaymentLedgerEntry paymentEntry = new PaymentLedgerEntry(
                claim,
                voucher,
                batch,
                PaymentLedgerEntryType.PAYER_PAYMENT,
                voucher.getPaidAmount().setScale(2, RoundingMode.HALF_UP),
                voucher.getPayerIcn(),
                batch.getCheckNumber(),
                batch.getCheckDate(),
                paymentNotes,
                actor
        );
        ledger.save(paymentEntry);

        // 5. If Contractual Adjustment exists, append ledger entry for contractual write-off
        if (voucher.getContractualAdj() != null && voucher.getContractualAdj().signum() > 0) {
            String coNotes = String.format("Contractual fee schedule write-off of $%s from check %s",
                    voucher.getContractualAdj().toPlainString(), batch.getCheckNumber());
            if (isFutureDated) {
                coNotes += " - Pending bank cash settlement";
            }
            PaymentLedgerEntry coEntry = new PaymentLedgerEntry(
                    claim,
                    voucher,
                    batch,
                    PaymentLedgerEntryType.CONTRACTUAL_WRITE_OFF,
                    voucher.getContractualAdj().setScale(2, RoundingMode.HALF_UP),
                    voucher.getPayerIcn(),
                    batch.getCheckNumber(),
                    batch.getCheckDate(),
                    coNotes,
                    actor
            );
            ledger.save(coEntry);
        }

        // 6. Mark Voucher as POSTED
        OffsetDateTime now = OffsetDateTime.now();
        voucher.setPostingStatus("POSTED");
        voucher.setPostedAt(now);
        voucher.setPostedBy(actor);
        vouchers.save(voucher);

        return new VoucherPostingResponse(
                voucher.getId(),
                claim.getId(),
                claim.getClaimNumber(),
                prevStatus,
                claim.getStatus(),
                voucher.getPaidAmount(),
                voucher.getPatientResponsibility(),
                voucher.getContractualAdj(),
                claim.getPaidAmount(),
                isFutureDated,
                settlementNote,
                actor,
                now
        );
    }

    private void validateVoucherForPosting(RemittanceClaimVoucher voucher) {
        if ("POSTED".equalsIgnoreCase(voucher.getPostingStatus())) {
            throw new ConflictException("Voucher " + voucher.getId() + " is already POSTED on claim "
                    + voucher.getClaimNumber() + ".");
        }

        if (!"BALANCED".equalsIgnoreCase(voucher.getBalanceStatus())) {
            throw new BusinessRuleException("Cannot post voucher " + voucher.getId()
                    + " because its balance status is '" + voucher.getBalanceStatus() + "'. Only BALANCED vouchers can be posted.");
        }

        if (voucher.getClaim() == null) {
            throw new BusinessRuleException("Cannot post voucher " + voucher.getId()
                    + " for claim number '" + voucher.getClaimNumber() + "' because no matched claim is linked.");
        }

        if (voucher.getPaidAmount() == null || voucher.getPaidAmount().signum() <= 0) {
            throw new BusinessRuleException("Cannot post voucher " + voucher.getId()
                    + " with paid amount " + voucher.getPaidAmount() + ". Zero-payment or denial-only vouchers cannot be posted automatically as payments.");
        }

        Claim claim = voucher.getClaim();
        ClaimStatus status = claim.getStatus();
        if (status == ClaimStatus.DRAFT || status == ClaimStatus.VOID || status == ClaimStatus.REJECTED) {
            throw new BusinessRuleException("Cannot post payment to claim " + claim.getClaimNumber()
                    + " with incompatible status " + status + ".");
        }

        // Verify Payer Consistency
        Payer remittancePayer = voucher.getRemittance().getPayer();
        if (!claim.getPayer().getId().equals(remittancePayer.getId())) {
            throw new ConflictException("Remittance payer '" + remittancePayer.getName()
                    + "' does not match claim payer '" + claim.getPayer().getName() + "'.");
        }

        // Verify Claim Number Exact Match
        if (!claim.getClaimNumber().trim().equalsIgnoreCase(voucher.getClaimNumber().trim())) {
            throw new ConflictException("Voucher claim number '" + voucher.getClaimNumber()
                    + "' does not match linked claim '" + claim.getClaimNumber() + "'.");
        }

        // Check Unique Ledger Constraint
        if (ledger.existsByVoucherIdAndEntryType(voucher.getId(), PaymentLedgerEntryType.PAYER_PAYMENT)) {
            throw new ConflictException("A payment ledger entry already exists for voucher " + voucher.getId() + ".");
        }
    }

    private PaymentLedgerResponse toLedgerResponse(PaymentLedgerEntry e) {
        return new PaymentLedgerResponse(
                e.getId(),
                e.getClaim().getId(),
                e.getVoucher() != null ? e.getVoucher().getId() : null,
                e.getRemittance() != null ? e.getRemittance().getId() : null,
                e.getEntryType(),
                e.getAmount(),
                e.getPayerIcn(),
                e.getCheckNumber(),
                e.getCheckDate(),
                e.getNotes(),
                e.getPostedBy(),
                e.getPostedAt()
        );
    }
}

