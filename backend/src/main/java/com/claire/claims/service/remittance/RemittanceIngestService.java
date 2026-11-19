package com.claire.claims.service.remittance;

import com.claire.claims.common.ApiExceptions.BusinessRuleException;
import com.claire.claims.common.ApiExceptions.ConflictException;
import com.claire.claims.common.ApiExceptions.NotFoundException;
import com.claire.claims.domain.*;
import com.claire.claims.dto.ClaimDtos.PageResponse;
import com.claire.claims.dto.RemittanceDtos.*;
import com.claire.claims.repository.*;
import com.claire.claims.security.CurrentUserProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class RemittanceIngestService {

    private final RemittanceBatchRepository batches;
    private final RemittanceClaimVoucherRepository vouchers;
    private final RemittanceClaimExceptionRepository exceptions;
    private final PayerRepository payers;
    private final ClaimRepository claims;
    private final CurrentUserProvider currentUser;

    public RemittanceIngestService(RemittanceBatchRepository batches,
                                   RemittanceClaimVoucherRepository vouchers,
                                   RemittanceClaimExceptionRepository exceptions,
                                   PayerRepository payers,
                                   ClaimRepository claims,
                                   CurrentUserProvider currentUser) {
        this.batches = batches;
        this.vouchers = vouchers;
        this.exceptions = exceptions;
        this.payers = payers;
        this.claims = claims;
        this.currentUser = currentUser;
    }

    // =====================================================================
    // Commands: Ingestion & Staging
    // =====================================================================

    /**
     * Staging ingest of an ERA 835 remittance batch.
     * Enforces duplicate detection, claim matching, and math balancing.
     * STRICT PHASE A GUARANTEE: Does NOT update claim or line balances.
     */
    @Transactional
    public RemittanceBatchDetailResponse ingest(ImportRemittanceRequest request, String rawPayload) {
        Payer payer = resolvePayer(request);

        // 1. Duplicate Remittance Detection (Payer + Check/EFT # + Check Date)
        if (batches.existsByPayerIdAndCheckNumberAndCheckDate(payer.getId(), request.checkNumber(), request.checkDate())) {
            throw new ConflictException(
                    "Remittance with check/EFT trace number '" + request.checkNumber()
                    + "' from payer '" + payer.getName()
                    + "' dated " + request.checkDate() + " was already imported.");
        }

        // 2. Validate batch total vs vouchers sum
        BigDecimal voucherPaidSum = BigDecimal.ZERO;
        for (RemittanceClaimInput c : request.claims()) {
            voucherPaidSum = voucherPaidSum.add(c.paidAmount());
        }
        boolean batchImbalance = request.totalPaid().compareTo(voucherPaidSum) != 0;

        RemittanceBatch batch = new RemittanceBatch();
        batch.setBatchControlNumber(mintBatchControlNumber(request.checkDate()));
        batch.setPayer(payer);
        batch.setPaymentMethod(request.paymentMethod() != null ? request.paymentMethod() : "ACH");
        batch.setCheckNumber(request.checkNumber());
        batch.setCheckDate(request.checkDate());
        batch.setTotalPaid(scale(request.totalPaid()));
        batch.setClaimsCount(request.claims().size());
        batch.setRawPayload(rawPayload);
        batch.setImportedBy(currentUser.username());
        batch.setStatus(batchImbalance ? RemittanceBatchStatus.OUT_OF_BALANCE : RemittanceBatchStatus.VALIDATED);

        int matchedCount = 0;
        int exceptionCount = 0;

        Set<String> voucherKeysInBatch = new HashSet<>();
        int seq = 1;

        for (RemittanceClaimInput cInput : request.claims()) {
            // Voucher-level duplicate prevention within same batch
            String voucherKey = cInput.claimNumber().trim() + "|" + (cInput.payerIcn() != null ? cInput.payerIcn().trim() : "NONE") + "|" + (cInput.entryType() != null ? cInput.entryType() : "ORIGINAL");
            if (!voucherKeysInBatch.add(voucherKey)) {
                throw new BusinessRuleException(
                        "Duplicate claim voucher for claim number '" + cInput.claimNumber()
                        + "' and ICN '" + cInput.payerIcn() + "' detected within the same remittance batch.");
            }

            RemittanceClaimVoucher voucher = new RemittanceClaimVoucher();
            voucher.setRemittance(batch);
            voucher.setSequenceNo(seq++);
            voucher.setClaimNumber(cInput.claimNumber().trim());
            voucher.setPayerIcn(cInput.payerIcn() != null ? cInput.payerIcn().trim() : null);
            voucher.setClpStatusCode(cInput.clpStatusCode().trim());
            voucher.setBilledAmount(scale(cInput.billedAmount()));
            voucher.setAllowedAmount(scale(cInput.allowedAmount()));
            voucher.setPaidAmount(scale(cInput.paidAmount()));
            voucher.setEntryType(cInput.entryType() != null ? cInput.entryType().toUpperCase() : "ORIGINAL");
            voucher.setPostingStatus("UNPOSTED"); // STRICT PHASE A: Staged only!

            // Accumulate adjustments from claim-level and line-level
            BigDecimal coSum = BigDecimal.ZERO;
            BigDecimal prSum = BigDecimal.ZERO;
            BigDecimal otherSum = BigDecimal.ZERO;

            List<RemittanceAdjustment> allAdjs = new ArrayList<>();

            // Claim-level adjustments
            if (cInput.adjustments() != null) {
                for (RemittanceAdjustmentInput aInput : cInput.adjustments()) {
                    RemittanceAdjustment adj = createAdjustment(voucher, null, aInput, voucher.getEntryType());
                    allAdjs.add(adj);
                    switch (aInput.groupCode()) {
                        case CO -> coSum = coSum.add(aInput.amount());
                        case PR -> prSum = prSum.add(aInput.amount());
                        case OA, PI -> otherSum = otherSum.add(aInput.amount());
                    }
                }
            }

            // Line-level adjustments
            if (cInput.lines() != null) {
                for (RemittanceLineInput lInput : cInput.lines()) {
                    if (lInput.adjustments() != null) {
                        for (RemittanceAdjustmentInput aInput : lInput.adjustments()) {
                            RemittanceAdjustment adj = createAdjustment(voucher, null, aInput, voucher.getEntryType());
                            allAdjs.add(adj);
                            switch (aInput.groupCode()) {
                                case CO -> coSum = coSum.add(aInput.amount());
                                case PR -> prSum = prSum.add(aInput.amount());
                                case OA, PI -> otherSum = otherSum.add(aInput.amount());
                            }
                        }
                    }
                }
            }

            // Determine effective patient responsibility
            BigDecimal effectivePr = cInput.patientResponsibility() != null && cInput.patientResponsibility().signum() > 0
                    ? scale(cInput.patientResponsibility())
                    : scale(prSum);

            voucher.setPatientResponsibility(effectivePr);
            voucher.setContractualAdj(scale(coSum));
            voucher.setOtherAdj(scale(otherSum));
            for (RemittanceAdjustment a : allAdjs) {
                voucher.addAdjustment(a);
            }

            // Mathematical Balancing Equation Check:
            // Billed == Paid + PR + CO + Other
            BigDecimal balanceSum = voucher.getPaidAmount()
                    .add(voucher.getPatientResponsibility())
                    .add(voucher.getContractualAdj())
                    .add(voucher.getOtherAdj());

            boolean isBalanced = voucher.getBilledAmount().subtract(balanceSum).abs().compareTo(new BigDecimal("0.01")) <= 0;
            if (!isBalanced) {
                voucher.setBalanceStatus("OUT_OF_BALANCE");
                batch.setStatus(RemittanceBatchStatus.OUT_OF_BALANCE);
                exceptionCount++;

                RemittanceClaimException exc = new RemittanceClaimException();
                exc.setRemittance(batch);
                exc.setVoucher(voucher);
                exc.setClaimNumber(voucher.getClaimNumber());
                exc.setBilledAmount(voucher.getBilledAmount());
                exc.setPaidAmount(voucher.getPaidAmount());
                exc.setExceptionType("OUT_OF_BALANCE");
                exc.setErrorMessage("Voucher balancing imbalance: Billed " + voucher.getBilledAmount()
                        + " != Paid " + voucher.getPaidAmount() + " + PR " + voucher.getPatientResponsibility()
                        + " + CO " + voucher.getContractualAdj() + " + Other " + voucher.getOtherAdj());
                batch.addException(exc);
            } else {
                voucher.setBalanceStatus("BALANCED");
            }

            // Claim Matching using stable claimNumber
            Optional<Claim> optClaim = claims.findByClaimNumber(cInput.claimNumber().trim());
            if (optClaim.isEmpty()) {
                voucher.setClaim(null);
                exceptionCount++;

                RemittanceClaimException exc = new RemittanceClaimException();
                exc.setRemittance(batch);
                exc.setVoucher(voucher);
                exc.setClaimNumber(cInput.claimNumber().trim());
                exc.setBilledAmount(cInput.billedAmount());
                exc.setPaidAmount(cInput.paidAmount());
                exc.setExceptionType("UNMATCHED_CLAIM");
                exc.setErrorMessage("No active claim found in system with number '" + cInput.claimNumber().trim() + "'");
                batch.addException(exc);
            } else {
                Claim claim = optClaim.get();
                voucher.setClaim(claim);

                // Verify Payer Ownership
                if (!claim.getPayer().getId().equals(payer.getId())) {
                    exceptionCount++;
                    RemittanceClaimException exc = new RemittanceClaimException();
                    exc.setRemittance(batch);
                    exc.setVoucher(voucher);
                    exc.setClaimNumber(claim.getClaimNumber());
                    exc.setBilledAmount(cInput.billedAmount());
                    exc.setPaidAmount(cInput.paidAmount());
                    exc.setExceptionType("PAYER_MISMATCH");
                    exc.setErrorMessage("Claim payer '" + claim.getPayer().getName() + "' does not match remittance payer '" + payer.getName() + "'");
                    batch.addException(exc);
                }

                // Verify Billed Amount Consistency
                if (cInput.billedAmount().compareTo(claim.getTotalCharge()) != 0) {
                    exceptionCount++;
                    RemittanceClaimException exc = new RemittanceClaimException();
                    exc.setRemittance(batch);
                    exc.setVoucher(voucher);
                    exc.setClaimNumber(claim.getClaimNumber());
                    exc.setBilledAmount(cInput.billedAmount());
                    exc.setPaidAmount(cInput.paidAmount());
                    exc.setExceptionType("CHARGE_MISMATCH");
                    exc.setErrorMessage("Remittance billed amount " + cInput.billedAmount() + " does not match claim total charge " + claim.getTotalCharge());
                    batch.addException(exc);
                }

                matchedCount++;
            }

            batch.addVoucher(voucher);
        }

        batch.setMatchedCount(matchedCount);
        batch.setExceptionCount(exceptionCount);

        RemittanceBatch saved = batches.save(batch);
        return toDetailResponse(saved);
    }

    private RemittanceAdjustment createAdjustment(RemittanceClaimVoucher voucher, ClaimLine line, RemittanceAdjustmentInput input, String entryType) {
        RemittanceAdjustment adj = new RemittanceAdjustment();
        adj.setVoucher(voucher);
        adj.setClaim(voucher.getClaim());
        adj.setClaimLine(line);
        adj.setGroupCode(input.groupCode());
        adj.setReasonCode(input.reasonCode().trim().toUpperCase());
        adj.setRemarkCode(input.remarkCode() != null ? input.remarkCode().trim().toUpperCase() : null);
        adj.setAmount(scale(input.amount()));
        adj.setEntryType(entryType);
        return adj;
    }

    // =====================================================================
    // Queries
    // =====================================================================

    @Transactional(readOnly = true)
    public PageResponse<RemittanceBatchListItem> search(Long payerId, RemittanceBatchStatus status, Pageable pageable) {
        Specification<RemittanceBatch> spec = (root, query, cb) -> cb.conjunction();
        if (payerId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("payer").get("id"), payerId));
        }
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }

        Page<RemittanceBatch> page = batches.findAll(spec, pageable);
        List<RemittanceBatchListItem> items = page.getContent().stream()
                .map(this::toListItem)
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
    public RemittanceBatchDetailResponse getById(Long id) {
        RemittanceBatch batch = batches.findWithDetailsById(id)
                .orElseThrow(() -> new NotFoundException("RemittanceBatch", id));
        return toDetailResponse(batch);
    }

    @Transactional(readOnly = true)
    public PageResponse<RemittanceExceptionResponse> listExceptions(Long remittanceId, String status, Pageable pageable) {
        Specification<RemittanceClaimException> spec = (root, query, cb) -> cb.conjunction();
        if (remittanceId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("remittance").get("id"), remittanceId));
        }
        if (status != null && !status.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status.trim().toUpperCase()));
        }

        Page<RemittanceClaimException> page = exceptions.findAll(spec, pageable);
        List<RemittanceExceptionResponse> items = page.getContent().stream()
                .map(this::toExceptionResponse)
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

    // =====================================================================
    // Helpers
    // =====================================================================

    private Payer resolvePayer(ImportRemittanceRequest request) {
        if (request.payerId() != null) {
            return payers.findById(request.payerId())
                    .orElseThrow(() -> new NotFoundException("Payer", request.payerId()));
        }
        if (request.payerCode() != null && !request.payerCode().isBlank()) {
            return payers.findByPayerCode(request.payerCode().trim())
                    .orElseThrow(() -> new BusinessRuleException("Payer not found for payerCode: '" + request.payerCode() + "'"));
        }
        throw new BusinessRuleException("Either payerId or payerCode is required to identify the remittance payer.");
    }

    private String mintBatchControlNumber(LocalDate date) {
        String datePrefix = date.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String rand = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        return "ERA-" + datePrefix + "-" + rand;
    }

    private BigDecimal scale(BigDecimal val) {
        return val != null ? val.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private RemittanceBatchListItem toListItem(RemittanceBatch b) {
        return new RemittanceBatchListItem(
                b.getId(),
                b.getBatchControlNumber(),
                b.getPayer().getId(),
                b.getPayer().getName(),
                b.getPaymentMethod(),
                b.getCheckNumber(),
                b.getCheckDate(),
                b.getTotalPaid(),
                b.getClaimsCount(),
                b.getMatchedCount(),
                b.getExceptionCount(),
                b.getStatus(),
                b.getImportedBy(),
                b.getImportedAt()
        );
    }

    private RemittanceBatchDetailResponse toDetailResponse(RemittanceBatch b) {
        List<RemittanceVoucherResponse> vList = vouchers.findByRemittanceIdOrderBySequenceNoAsc(b.getId()).stream()
                .map(this::toVoucherResponse)
                .toList();
        List<RemittanceExceptionResponse> eList = exceptions.findByRemittanceIdOrderByIdAsc(b.getId()).stream()
                .map(this::toExceptionResponse)
                .toList();

        return new RemittanceBatchDetailResponse(
                b.getId(),
                b.getBatchControlNumber(),
                b.getPayer().getId(),
                b.getPayer().getName(),
                b.getPaymentMethod(),
                b.getCheckNumber(),
                b.getCheckDate(),
                b.getTotalPaid(),
                b.getClaimsCount(),
                b.getMatchedCount(),
                b.getExceptionCount(),
                b.getStatus(),
                b.getImportedBy(),
                b.getImportedAt(),
                vList,
                eList
        );
    }

    private RemittanceVoucherResponse toVoucherResponse(RemittanceClaimVoucher v) {
        List<RemittanceAdjustmentResponse> adjs = v.getAdjustments().stream()
                .map(a -> new RemittanceAdjustmentResponse(
                        a.getId(),
                        a.getClaimLine() != null ? a.getClaimLine().getId() : null,
                        a.getGroupCode(),
                        a.getReasonCode(),
                        a.getRemarkCode(),
                        a.getAmount(),
                        a.getEntryType()
                ))
                .toList();

        return new RemittanceVoucherResponse(
                v.getId(),
                v.getClaim() != null ? v.getClaim().getId() : null,
                v.getSequenceNo(),
                v.getClaimNumber(),
                v.getPayerIcn(),
                v.getClpStatusCode(),
                v.getBilledAmount(),
                v.getAllowedAmount(),
                v.getPaidAmount(),
                v.getPatientResponsibility(),
                v.getContractualAdj(),
                v.getOtherAdj(),
                v.getEntryType(),
                v.getBalanceStatus(),
                v.getPostingStatus(),
                v.getPostedAt(),
                v.getPostedBy(),
                adjs
        );
    }

    private RemittanceExceptionResponse toExceptionResponse(RemittanceClaimException e) {
        return new RemittanceExceptionResponse(
                e.getId(),
                e.getRemittance().getId(),
                e.getVoucher() != null ? e.getVoucher().getId() : null,
                e.getClaimNumber(),
                e.getPatientName(),
                e.getServiceDate(),
                e.getBilledAmount(),
                e.getPaidAmount(),
                e.getExceptionType(),
                e.getErrorMessage(),
                e.getStatus(),
                e.getResolvedAt(),
                e.getResolvedBy()
        );
    }
}

