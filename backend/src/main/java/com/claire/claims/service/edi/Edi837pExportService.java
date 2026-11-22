package com.claire.claims.service.edi;

import com.claire.claims.common.ApiExceptions.BusinessRuleException;
import com.claire.claims.common.ApiExceptions.ConflictException;
import com.claire.claims.common.ApiExceptions.NotFoundException;
import com.claire.claims.domain.*;
import com.claire.claims.dto.Edi837pDtos.*;
import com.claire.claims.repository.ClaimRepository;
import com.claire.claims.repository.EdiExportBatchClaimRepository;
import com.claire.claims.repository.EdiExportBatchRepository;
import com.claire.claims.repository.PayerRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class Edi837pExportService {

    private final ClaimRepository claimRepository;
    private final PayerRepository payerRepository;
    private final EdiExportBatchRepository batchRepository;
    private final EdiExportBatchClaimRepository batchClaimRepository;
    private final Synthetic837pGenerator generator;

    public Edi837pExportService(
            ClaimRepository claimRepository,
            PayerRepository payerRepository,
            EdiExportBatchRepository batchRepository,
            EdiExportBatchClaimRepository batchClaimRepository,
            Synthetic837pGenerator generator
    ) {
        this.claimRepository = claimRepository;
        this.payerRepository = payerRepository;
        this.batchRepository = batchRepository;
        this.batchClaimRepository = batchClaimRepository;
        this.generator = generator;
    }

    /**
     * Validates a collection of claims for EDI 837P export readiness without mutating state.
     */
    public EdiValidationResponse validateClaims(ValidateClaimsRequest request) {
        if (request.claimIds() == null || request.claimIds().isEmpty()) {
            throw new BusinessRuleException("claimIds list must not be empty");
        }

        List<ClaimValidationResult> results = new ArrayList<>();
        BigDecimal totalCharge = BigDecimal.ZERO;
        int validCount = 0;
        int errorCount = 0;

        for (Long claimId : request.claimIds()) {
            Optional<Claim> optClaim = claimRepository.findWithReferencesById(claimId);
            if (optClaim.isEmpty()) {
                results.add(new ClaimValidationResult(
                        claimId,
                        "UNKNOWN",
                        false,
                        List.of("Claim with ID " + claimId + " was not found"),
                        BigDecimal.ZERO,
                        "Unknown",
                        "Unknown"
                ));
                errorCount++;
                continue;
            }

            Claim claim = optClaim.get();
            List<String> errors = validateSingleClaim(claim, request.payerId());
            boolean isValid = errors.isEmpty();
            if (isValid) {
                validCount++;
                totalCharge = totalCharge.add(claim.getTotalCharge());
            } else {
                errorCount++;
            }

            results.add(new ClaimValidationResult(
                    claim.getId(),
                    claim.getClaimNumber(),
                    isValid,
                    errors,
                    claim.getTotalCharge(),
                    claim.getPatient() != null ? claim.getPatient().getDisplayName() : "Unknown",
                    claim.getPayer() != null ? claim.getPayer().getName() : "Unknown"
            ));
        }

        return new EdiValidationResponse(
                errorCount == 0,
                validCount,
                errorCount,
                totalCharge,
                results
        );
    }

    /**
     * Executes the generation and persisting of an EDI 837P export batch.
     */
    @Transactional
    public EdiExportBatchDetailResponse export837p(Export837pRequest request, String currentUsername) {
        if (request.claimIds() == null || request.claimIds().isEmpty()) {
            throw new BusinessRuleException("claimIds list must not be empty");
        }

        // Check for duplicate claim IDs in the request
        Set<Long> uniqueIds = new HashSet<>(request.claimIds());
        if (uniqueIds.size() != request.claimIds().size()) {
            throw new BusinessRuleException("Duplicate claim IDs detected in export request");
        }

        // Fetch claims
        List<Claim> claims = new ArrayList<>();
        for (Long claimId : request.claimIds()) {
            Claim claim = claimRepository.findWithReferencesById(claimId)
                    .orElseThrow(() -> new NotFoundException("Claim", claimId));
            claims.add(claim);
        }

        // Validate payer consistency
        Long rawPayerId = request.payerId();
        if (rawPayerId == null && !claims.isEmpty()) {
            rawPayerId = claims.get(0).getPayer().getId();
        }
        final Long commonPayerId = rawPayerId;

        for (Claim c : claims) {
            if (!c.getPayer().getId().equals(commonPayerId)) {
                throw new BusinessRuleException("Mixed-payer export batches are not supported. All claims must belong to the same payer.");
            }
        }

        Payer payer = payerRepository.findById(commonPayerId)
                .orElseThrow(() -> new NotFoundException("Payer", commonPayerId));

        // Validate each claim
        List<String> allValidationErrors = new ArrayList<>();
        for (Claim c : claims) {
            List<String> errors = validateSingleClaim(c, commonPayerId);
            if (!errors.isEmpty()) {
                allValidationErrors.add("Claim " + c.getClaimNumber() + ": " + String.join(", ", errors));
            }
        }

        if (!allValidationErrors.isEmpty()) {
            throw new ConflictException("EDI 837P export validation failed:\n" + String.join("\n", allValidationErrors));
        }

        // Duplicate prevention unless allowRebill is set
        boolean allowRebill = Boolean.TRUE.equals(request.allowRebill());
        if (!allowRebill) {
            List<Long> alreadyExported = batchClaimRepository.findAlreadyExportedClaimIds(request.claimIds());
            if (!alreadyExported.isEmpty()) {
                throw new ConflictException("Claims " + alreadyExported + " have already been exported. Pass allowRebill=true to force re-export.");
            }
        }

        OffsetDateTime now = OffsetDateTime.now();
        long nextSeq = batchRepository.countTotalBatches() + 1;
        String batchNumber = String.format("837P-%s-%06d", now.format(DateTimeFormatter.ofPattern("yyyyMMdd")), nextSeq);

        // Generate synthetic X12 837P payload
        Synthetic837pGenerator.GeneratorResult genResult = generator.generate(claims, payer, nextSeq, now);

        String contentHash = sha256(genResult.payload());
        String fileName = String.format("claimflow-837p-%s-%04d.837", now.format(DateTimeFormatter.ofPattern("yyyyMMdd")), nextSeq);

        BigDecimal totalBilledAmount = claims.stream()
                .map(Claim::getTotalCharge)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        EdiExportBatch batch = new EdiExportBatch();
        batch.setBatchNumber(batchNumber);
        batch.setPayer(payer);
        batch.setInterchangeControlNumber(genResult.interchangeControlNumber());
        batch.setGroupControlNumber(genResult.groupControlNumber());
        batch.setTransactionControlNumber(genResult.transactionControlNumber());
        batch.setExportStatus("EXPORTED");
        batch.setClaimCount(claims.size());
        batch.setTotalBilledAmount(totalBilledAmount);
        batch.setFileName(fileName);
        batch.setContentHash(contentHash);
        batch.setPayload(genResult.payload());
        batch.setCreatedBy(currentUsername != null ? currentUsername : "system");
        batch.setCreatedAt(now);
        batch.setExportedAt(now);
        batch.setValidationSummary(claims.size() + " claims successfully validated and generated in synthetic X12 837P format.");

        int seq = 1;
        for (Claim c : claims) {
            EdiExportBatchClaim bc = new EdiExportBatchClaim();
            bc.setBatch(batch);
            bc.setClaim(c);
            bc.setClaimNumberSnapshot(c.getClaimNumber());
            bc.setClaimChargeSnapshot(c.getTotalCharge());
            bc.setTransactionSequence(seq++);
            bc.setExportedAt(now);
            batch.getClaims().add(bc);
        }

        EdiExportBatch saved = batchRepository.save(batch);

        return mapToDetailResponse(saved);
    }

    public Page<EdiExportBatchSummary> getExports(Long payerId, String status, Pageable pageable) {
        return batchRepository.findFiltered(payerId, status, pageable)
                .map(this::mapToSummary);
    }

    public EdiExportBatchDetailResponse getExportById(Long id) {
        EdiExportBatch batch = batchRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("EdiExportBatch", id));
        return mapToDetailResponse(batch);
    }

    public ExportFileResult getExportFile(Long id) {
        EdiExportBatch batch = batchRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("EdiExportBatch", id));
        return new ExportFileResult(batch.getFileName(), batch.getPayload(), batch.getContentHash());
    }

    public record ExportFileResult(String fileName, String payload, String contentHash) {}

    public EdiExportKpis getKpis() {
        long eligible = claimRepository.countByStatus(ClaimStatus.SUBMITTED);
        long totalBatches = batchRepository.countTotalBatches();
        long totalExportedClaims = batchRepository.sumExportedClaimsCount();
        BigDecimal totalAmount = batchRepository.sumTotalBilledAmount();
        return new EdiExportKpis(eligible, totalBatches, totalExportedClaims, totalAmount, 0L);
    }

    public Page<EligibleClaimDto> getEligibleClaims(Long payerId, Pageable pageable) {
        // Query claims in SUBMITTED status
        Page<Claim> page = claimRepository.findAll((root, query, cb) -> {
            var predicate = cb.equal(root.get("status"), ClaimStatus.SUBMITTED);
            if (payerId != null) {
                predicate = cb.and(predicate, cb.equal(root.get("payer").get("id"), payerId));
            }
            return predicate;
        }, pageable);

        List<EligibleClaimDto> dtos = page.getContent().stream().map(c -> {
            List<EdiExportBatchClaim> exports = batchClaimRepository.findLatestByClaimId(c.getId());
            boolean prevExported = !exports.isEmpty();
            OffsetDateTime lastExport = prevExported ? exports.get(0).getExportedAt() : null;
            return new EligibleClaimDto(
                    c.getId(),
                    c.getClaimNumber(),
                    c.getPatient() != null ? c.getPatient().getDisplayName() : "Unknown",
                    c.getPatient() != null ? c.getPatient().getId() : null,
                    c.getPayer() != null ? c.getPayer().getName() : "Unknown",
                    c.getPayer() != null ? c.getPayer().getId() : null,
                    c.getProvider() != null ? c.getProvider().getDisplayName() : "Unknown",
                    c.getProvider() != null ? c.getProvider().getId() : null,
                    c.getServiceDateFrom(),
                    c.getServiceDateTo(),
                    c.getStatus().name(),
                    c.getTotalCharge(),
                    prevExported,
                    lastExport
            );
        }).toList();

        return new PageImpl<>(dtos, pageable, page.getTotalElements());
    }

    private List<String> validateSingleClaim(Claim claim, Long expectedPayerId) {
        List<String> errors = new ArrayList<>();

        if (claim.getStatus() != ClaimStatus.SUBMITTED) {
            errors.add("Claim status is " + claim.getStatus() + "; only SUBMITTED claims are eligible for EDI 837P export.");
        }

        if (expectedPayerId != null && claim.getPayer() != null && !claim.getPayer().getId().equals(expectedPayerId)) {
            errors.add("Claim belongs to payer '" + claim.getPayer().getName() + "', not the selected batch payer.");
        }

        // Patient validation
        Patient patient = claim.getPatient();
        if (patient == null) {
            errors.add("Missing patient information.");
        } else {
            if (patient.getFirstName() == null || patient.getFirstName().isBlank()) errors.add("Missing patient first name.");
            if (patient.getLastName() == null || patient.getLastName().isBlank()) errors.add("Missing patient last name.");
            if (patient.getMrn() == null || patient.getMrn().isBlank()) errors.add("Missing patient MRN.");
            if (patient.getDateOfBirth() == null) errors.add("Missing patient date of birth.");
        }

        // Payer validation
        Payer payer = claim.getPayer();
        if (payer == null) {
            errors.add("Missing payer information.");
        } else {
            if (payer.getPayerCode() == null || payer.getPayerCode().isBlank()) errors.add("Missing payer identification code.");
            if (payer.getName() == null || payer.getName().isBlank()) errors.add("Missing payer name.");
        }

        // Provider validation
        Provider provider = claim.getProvider();
        if (provider == null) {
            errors.add("Missing billing provider.");
        } else {
            if (provider.getNpi() == null || !provider.getNpi().matches("^\\d{10}$")) {
                errors.add("Invalid provider NPI: must be exactly 10 digits.");
            }
            if (provider.getTaxId() == null || provider.getTaxId().isBlank()) {
                errors.add("Missing provider federal Tax ID (EIN/SSN).");
            }
        }

        // Subscriber / Policy validation
        InsurancePolicy policy = claim.getPolicy();
        if (policy == null || policy.getMemberId() == null || policy.getMemberId().isBlank()) {
            if (patient == null || patient.getMrn() == null) {
                errors.add("Missing insurance subscriber/member identification.");
            }
        }

        // Service Dates
        if (claim.getServiceDateFrom() == null) {
            errors.add("Missing service start date.");
        }
        if (claim.getServiceDateTo() == null) {
            errors.add("Missing service end date.");
        }
        if (claim.getServiceDateFrom() != null && claim.getServiceDateTo() != null &&
                claim.getServiceDateFrom().isAfter(claim.getServiceDateTo())) {
            errors.add("Service start date cannot be after service end date.");
        }

        // Service Lines
        List<ClaimLine> lines = claim.getLines();
        if (lines == null || lines.isEmpty()) {
            errors.add("Claim must have at least one billed service line.");
        } else {
            BigDecimal computedSum = BigDecimal.ZERO;
            for (ClaimLine line : lines) {
                if (line.getCptCode() == null || line.getCptCode().isBlank()) {
                    errors.add("Line " + line.getLineNumber() + " is missing CPT/HCPCS procedure code.");
                }
                if (line.getUnits() == null || line.getUnits() <= 0) {
                    errors.add("Line " + line.getLineNumber() + " must have units >= 1.");
                }
                if (line.getChargeAmount() == null || line.getChargeAmount().compareTo(BigDecimal.ZERO) < 0) {
                    errors.add("Line " + line.getLineNumber() + " charge cannot be negative.");
                } else {
                    computedSum = computedSum.add(line.getChargeAmount());
                }
            }

            if (claim.getTotalCharge() == null || claim.getTotalCharge().compareTo(computedSum) != 0) {
                errors.add("Claim total charge ($" + claim.getTotalCharge() + ") does not match sum of service lines ($" + computedSum + ").");
            }
        }

        // Diagnoses
        List<ClaimDiagnosis> diagnoses = claim.getDiagnoses();
        if (diagnoses == null || diagnoses.isEmpty()) {
            errors.add("Claim must have at least one ICD-10 diagnosis code.");
        }

        return errors;
    }

    private EdiExportBatchSummary mapToSummary(EdiExportBatch b) {
        return new EdiExportBatchSummary(
                b.getId(),
                b.getBatchNumber(),
                b.getPayer() != null ? b.getPayer().getId() : null,
                b.getPayer() != null ? b.getPayer().getName() : "Unknown Payer",
                b.getExportStatus(),
                b.getClaimCount(),
                b.getTotalBilledAmount(),
                b.getFileName(),
                b.getExportedAt(),
                b.getCreatedBy()
        );
    }

    private EdiExportBatchDetailResponse mapToDetailResponse(EdiExportBatch b) {
        List<ExportedClaimItem> claimItems = b.getClaims().stream()
                .map(c -> new ExportedClaimItem(
                        c.getClaim().getId(),
                        c.getClaimNumberSnapshot(),
                        c.getClaimChargeSnapshot(),
                        c.getTransactionSequence(),
                        c.getExportedAt()
                ))
                .toList();

        return new EdiExportBatchDetailResponse(
                b.getId(),
                b.getBatchNumber(),
                b.getPayer() != null ? b.getPayer().getId() : null,
                b.getPayer() != null ? b.getPayer().getName() : "Unknown Payer",
                b.getInterchangeControlNumber(),
                b.getGroupControlNumber(),
                b.getTransactionControlNumber(),
                b.getExportStatus(),
                b.getClaimCount(),
                b.getTotalBilledAmount(),
                b.getFileName(),
                b.getContentHash(),
                b.getExportedAt(),
                b.getCreatedBy(),
                b.getValidationSummary(),
                claimItems,
                "/api/edi/837p/exports/" + b.getId() + "/download"
        );
    }

    private static String sha256(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
