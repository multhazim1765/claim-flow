package com.claire.claims.service.remittance;

import com.claire.claims.common.ApiExceptions.BusinessRuleException;
import com.claire.claims.common.ApiExceptions.ConflictException;
import com.claire.claims.common.ApiExceptions.NotFoundException;
import com.claire.claims.domain.*;
import com.claire.claims.dto.PaymentReversalDtos.PaymentReversalRequest;
import com.claire.claims.dto.PaymentReversalDtos.PaymentReversalResponse;
import com.claire.claims.repository.ClaimRepository;
import com.claire.claims.repository.ClaimStatusHistoryRepository;
import com.claire.claims.repository.PaymentLedgerRepository;
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
import java.util.List;
import java.util.Optional;

@Service
public class PaymentReversalService {

    private final ClaimRepository claimRepository;
    private final PaymentLedgerRepository ledgerRepository;
    private final ClaimStatusHistoryRepository statusHistoryRepository;
    private final ClaimStatusMachine statusMachine;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    @Autowired
    public PaymentReversalService(ClaimRepository claimRepository,
                                  PaymentLedgerRepository ledgerRepository,
                                  ClaimStatusHistoryRepository statusHistoryRepository,
                                  ClaimStatusMachine statusMachine,
                                  CurrentUserProvider currentUserProvider) {
        this(claimRepository, ledgerRepository, statusHistoryRepository, statusMachine, currentUserProvider, Clock.systemDefaultZone());
    }

    public PaymentReversalService(ClaimRepository claimRepository,
                                  PaymentLedgerRepository ledgerRepository,
                                  ClaimStatusHistoryRepository statusHistoryRepository,
                                  ClaimStatusMachine statusMachine,
                                  CurrentUserProvider currentUserProvider,
                                  Clock clock) {
        this.claimRepository = claimRepository;
        this.ledgerRepository = ledgerRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.statusMachine = statusMachine;
        this.currentUserProvider = currentUserProvider;
        this.clock = clock;
    }

    @Transactional
    public PaymentReversalResponse reversePayment(Long claimId, PaymentReversalRequest request) {
        Claim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new NotFoundException("Claim", claimId));

        PaymentLedgerEntry originalPayment = ledgerRepository.findById(request.originalLedgerId())
                .orElseThrow(() -> new NotFoundException("PaymentLedgerEntry", request.originalLedgerId()));

        if (!originalPayment.getClaim().getId().equals(claim.getId())) {
            throw new BusinessRuleException("Ledger entry " + request.originalLedgerId()
                    + " does not belong to claim " + claimId);
        }

        if (originalPayment.getEntryType() == PaymentLedgerEntryType.LEGACY_INITIAL_BALANCE) {
            throw new BusinessRuleException("Reversal of legacy initial baseline balances is not supported.");
        }

        if (originalPayment.getEntryType() != PaymentLedgerEntryType.PAYER_PAYMENT) {
            throw new BusinessRuleException("Only PAYER_PAYMENT ledger entries can be reversed. Found entry type: "
                    + originalPayment.getEntryType());
        }

        if (ledgerRepository.existsByOriginalLedgerEntryIdAndEntryType(originalPayment.getId(), PaymentLedgerEntryType.PAYER_REVERSAL)) {
            throw new ConflictException("Payment ledger entry " + originalPayment.getId()
                    + " has already been reversed.");
        }

        if (originalPayment.getVoucher() != null && !"POSTED".equalsIgnoreCase(originalPayment.getVoucher().getPostingStatus())) {
            throw new BusinessRuleException("Cannot reverse a payment whose voucher is not in POSTED status: "
                    + originalPayment.getVoucher().getPostingStatus());
        }

        BigDecimal paymentAmount = originalPayment.getAmount();
        BigDecimal newPaidAmount = claim.getPaidAmount().subtract(paymentAmount).setScale(2, RoundingMode.HALF_UP);
        if (newPaidAmount.signum() < 0) {
            throw new BusinessRuleException("Reversal would result in negative cumulative payment balance: " + newPaidAmount);
        }

        String actor = currentUserProvider.username();
        String effectiveCheckNumber = request.checkNumber() != null && !request.checkNumber().isBlank()
                ? request.checkNumber().trim()
                : (originalPayment.getCheckNumber() != null ? "REV-" + originalPayment.getCheckNumber() : "REV-MANUAL");
        LocalDate effectiveCheckDate = request.checkDate() != null
                ? request.checkDate()
                : LocalDate.now(clock);

        // 1. Create and persist signed negative PAYER_REVERSAL ledger entry
        PaymentLedgerEntry reversalEntry = new PaymentLedgerEntry();
        reversalEntry.setClaim(claim);
        reversalEntry.setVoucher(originalPayment.getVoucher());
        reversalEntry.setRemittance(originalPayment.getRemittance());
        reversalEntry.setEntryType(PaymentLedgerEntryType.PAYER_REVERSAL);
        reversalEntry.setAmount(paymentAmount.negate());
        reversalEntry.setPayerIcn(originalPayment.getPayerIcn());
        reversalEntry.setCheckNumber(effectiveCheckNumber);
        reversalEntry.setCheckDate(effectiveCheckDate);
        reversalEntry.setOriginalLedgerEntry(originalPayment);
        reversalEntry.setNotes("Payment reversal: " + request.reversalReason().trim());
        reversalEntry.setPostedBy(actor);
        reversalEntry.setPostedAt(OffsetDateTime.now(clock));
        reversalEntry = ledgerRepository.save(reversalEntry);

        // 2. Handle Contractual Adjustment reversal if applicable
        BigDecimal reversedCoAdjustment = BigDecimal.ZERO;
        Long coReversalId = null;
        if (originalPayment.getVoucher() != null) {
            Optional<PaymentLedgerEntry> coWriteOffOpt = ledgerRepository.findByVoucherIdAndEntryType(
                    originalPayment.getVoucher().getId(), PaymentLedgerEntryType.CONTRACTUAL_WRITE_OFF);

            if (coWriteOffOpt.isPresent()) {
                PaymentLedgerEntry coWriteOff = coWriteOffOpt.get();
                if (!ledgerRepository.existsByOriginalLedgerEntryIdAndEntryType(
                        coWriteOff.getId(), PaymentLedgerEntryType.CONTRACTUAL_ADJUSTMENT_REVERSAL)) {
                    reversedCoAdjustment = coWriteOff.getAmount();
                    PaymentLedgerEntry coReversal = new PaymentLedgerEntry();
                    coReversal.setClaim(claim);
                    coReversal.setVoucher(originalPayment.getVoucher());
                    coReversal.setRemittance(originalPayment.getRemittance());
                    coReversal.setEntryType(PaymentLedgerEntryType.CONTRACTUAL_ADJUSTMENT_REVERSAL);
                    coReversal.setAmount(reversedCoAdjustment.negate());
                    coReversal.setPayerIcn(coWriteOff.getPayerIcn());
                    coReversal.setCheckNumber(effectiveCheckNumber);
                    coReversal.setCheckDate(effectiveCheckDate);
                    coReversal.setOriginalLedgerEntry(coWriteOff);
                    coReversal.setNotes("Contractual adjustment reversal: " + request.reversalReason().trim());
                    coReversal.setPostedBy(actor);
                    coReversal.setPostedAt(OffsetDateTime.now(clock));
                    coReversal = ledgerRepository.save(coReversal);
                    coReversalId = coReversal.getId();
                }
            }
        }

        // 3. Claim State Machine Reopening & Financial Balance Updates
        ClaimStatus previousStatus = claim.getStatus();
        List<ClaimStatusHistory> transitions = statusMachine.applyPaymentReversal(
                claim, newPaidAmount, effectiveCheckNumber, actor);
        if (!transitions.isEmpty()) {
            statusHistoryRepository.saveAll(transitions);
        }

        BigDecimal newContractualAdjustment = claim.getContractualAdjustment()
                .subtract(reversedCoAdjustment)
                .max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);

        claim.setPaidAmount(newPaidAmount);
        claim.setContractualAdjustment(newContractualAdjustment);

        if (claim.getStatus() == ClaimStatus.ACCEPTED && newPaidAmount.signum() == 0) {
            claim.setAllowedAmount(BigDecimal.ZERO);
            claim.setPatientResponsibility(BigDecimal.ZERO);
        } else if (claim.getAllowedAmount() != null && claim.getAllowedAmount().signum() > 0) {
            BigDecimal newPatientResp = claim.getAllowedAmount().subtract(newPaidAmount).max(BigDecimal.ZERO);
            claim.setPatientResponsibility(newPatientResp.setScale(2, RoundingMode.HALF_UP));
        }

        claimRepository.save(claim);

        return new PaymentReversalResponse(
                claim.getId(),
                claim.getClaimNumber(),
                originalPayment.getId(),
                paymentAmount,
                reversedCoAdjustment,
                claim.getPaidAmount(),
                claim.getContractualAdjustment(),
                previousStatus,
                claim.getStatus(),
                reversalEntry.getId(),
                coReversalId,
                request.reversalReason().trim(),
                reversalEntry.getPostedAt(),
                actor
        );
    }
}

