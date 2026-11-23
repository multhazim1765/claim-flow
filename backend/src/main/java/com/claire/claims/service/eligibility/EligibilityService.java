package com.claire.claims.service.eligibility;

import com.claire.claims.common.ApiExceptions.NotFoundException;
import com.claire.claims.domain.EligibilityCheck;
import com.claire.claims.domain.InsurancePolicy;
import com.claire.claims.domain.Patient;
import com.claire.claims.domain.Payer;
import com.claire.claims.dto.EligibilityDtos.EligibilityCheckRequest;
import com.claire.claims.dto.EligibilityDtos.EligibilityCheckResponse;
import com.claire.claims.dto.EligibilityDtos.EligibilityHistoryItem;
import com.claire.claims.repository.EligibilityCheckRepository;
import com.claire.claims.repository.InsurancePolicyRepository;
import com.claire.claims.repository.PatientRepository;
import com.claire.claims.repository.PayerRepository;
import com.claire.claims.security.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class EligibilityService {

    private final EligibilityCheckRepository eligibilityChecks;
    private final PatientRepository patients;
    private final PayerRepository payers;
    private final InsurancePolicyRepository policies;
    private final EligibilityGateway gateway;
    private final CurrentUserProvider currentUser;

    public EligibilityService(EligibilityCheckRepository eligibilityChecks,
                              PatientRepository patients,
                              PayerRepository payers,
                              InsurancePolicyRepository policies,
                              EligibilityGateway gateway,
                              CurrentUserProvider currentUser) {
        this.eligibilityChecks = eligibilityChecks;
        this.patients = patients;
        this.payers = payers;
        this.policies = policies;
        this.gateway = gateway;
        this.currentUser = currentUser;
    }

    /**
     * Checks real-time coverage against the payer.
     * Enforces an enhanced multi-dimensional cache matching exact patient, payer, policy,
     * service date, service type code, and configuration fingerprint within the 24-hour freshness window.
     */
    public EligibilityCheckResponse checkEligibility(EligibilityCheckRequest request) {
        Patient patient = patients.findById(request.patientId())
                .orElseThrow(() -> new NotFoundException("Patient", request.patientId()));
        Payer payer = payers.findById(request.payerId())
                .orElseThrow(() -> new NotFoundException("Payer", request.payerId()));

        InsurancePolicy policy;
        if (request.policyId() != null) {
            policy = policies.findById(request.policyId())
                    .filter(p -> p.getPatient().getId().equals(patient.getId()) && p.getPayer().getId().equals(payer.getId()))
                    .orElseThrow(() -> new NotFoundException("InsurancePolicy", request.policyId()));
        } else {
            policy = policies.findByPatientIdOrderByPriorityAsc(patient.getId()).stream()
                    .filter(p -> p.getPayer().getId().equals(payer.getId()))
                    .findFirst()
                    .orElse(null);
        }

        String serviceTypeCode = request.serviceTypeCode() != null && !request.serviceTypeCode().isBlank()
                ? request.serviceTypeCode()
                : "30";

        String fingerprint = computeCacheFingerprint(payer, policy);
        Long policyId = policy != null ? policy.getId() : null;

        // Check 24-hour enhanced multi-dimensional cache
        if (!Boolean.TRUE.equals(request.forceRefresh())) {
            OffsetDateTime cacheThreshold = OffsetDateTime.now().minusHours(24);
            List<EligibilityCheck> matches = eligibilityChecks.findMatchingCachedChecks(
                    patient.getId(), payer.getId(), policyId, request.serviceDate(),
                    serviceTypeCode, fingerprint, cacheThreshold);

            if (!matches.isEmpty()) {
                return toResponse(matches.get(0), true);
            }
        }

        // Cache miss or force refresh: execute gateway verification
        EligibilityGateway.GatewayResponse gw = gateway.verify(
                patient, payer, policy, request.serviceDate(), serviceTypeCode);

        EligibilityCheck check = new EligibilityCheck();
        check.setPatient(patient);
        check.setPayer(payer);
        check.setPolicy(policy);
        check.setServiceDate(request.serviceDate());
        check.setServiceTypeCode(serviceTypeCode);
        check.setActive(gw.active());
        check.setCheckedAt(OffsetDateTime.now());
        check.setPlanBegin(gw.planBegin());
        check.setPlanEnd(gw.planEnd());
        check.setCopay(gw.copay());
        check.setCoinsurancePercent(gw.coinsurancePercent());
        check.setDeductibleTotal(gw.deductibleTotal());
        check.setDeductibleRemaining(gw.deductibleRemaining());
        check.setOutOfPocketRemaining(gw.outOfPocketRemaining());
        check.setRawResponse(gw.rawResponse());
        check.setCheckedBy(currentUser.username());
        check.setCached(false);
        check.setBenefitSource(gw.benefitSource() != null ? gw.benefitSource() : "SIMULATED_DEFAULT");
        check.setCacheFingerprint(fingerprint);

        EligibilityCheck saved = eligibilityChecks.save(check);
        return toResponse(saved, false);
    }

    /**
     * Deterministic SHA-256 fingerprint representing policy member ID, active status,
     * coverage dates, payer plan type, and demonstration benefit overrides.
     * Any change immediately invalidates cached eligibility responses.
     */
    public static String computeCacheFingerprint(Payer payer, InsurancePolicy policy) {
        String planType = payer != null && payer.getPlanType() != null ? payer.getPlanType().name() : "NONE";
        String memberId = policy != null && policy.getMemberId() != null ? policy.getMemberId().trim() : "NONE";
        String eff = policy != null && policy.getEffectiveDate() != null ? policy.getEffectiveDate().toString() : "NONE";
        String term = policy != null && policy.getTerminationDate() != null ? policy.getTerminationDate().toString() : "NONE";
        String polActive = policy != null ? String.valueOf(policy.isActive()) : "TRUE";
        String demoCopay = policy != null && policy.getDemoCopay() != null ? policy.getDemoCopay().toPlainString() : "NONE";
        String demoCoins = policy != null && policy.getDemoCoinsurancePercent() != null ? String.valueOf(policy.getDemoCoinsurancePercent()) : "NONE";
        String demoDedTot = policy != null && policy.getDemoDeductibleTotal() != null ? policy.getDemoDeductibleTotal().toPlainString() : "NONE";
        String demoDedRem = policy != null && policy.getDemoDeductibleRemaining() != null ? policy.getDemoDeductibleRemaining().toPlainString() : "NONE";
        String demoOopRem = policy != null && policy.getDemoOutOfPocketRemaining() != null ? policy.getDemoOutOfPocketRemaining().toPlainString() : "NONE";

        String composite = String.join("|",
                planType, memberId, eff, term, polActive,
                demoCopay, demoCoins, demoDedTot, demoDedRem, demoOopRem);

        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(composite.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(composite.hashCode());
        }
    }

    /**
     * Complete audit trail of eligibility checks for a patient.
     */
    @Transactional(readOnly = true)
    public List<EligibilityHistoryItem> getHistory(Long patientId) {
        if (!patients.existsById(patientId)) {
            throw new NotFoundException("Patient", patientId);
        }
        return eligibilityChecks.findByPatientIdOrderByCheckedAtDesc(patientId).stream()
                .map(this::toHistoryItem)
                .toList();
    }

    /**
     * Resolves the latest check for a patient (optionally constrained by payer).
     */
    @Transactional(readOnly = true)
    public Optional<EligibilityCheckResponse> getLatest(Long patientId, Long payerId) {
        if (!patients.existsById(patientId)) {
            throw new NotFoundException("Patient", patientId);
        }
        Optional<EligibilityCheck> check = payerId != null
                ? eligibilityChecks.findFirstByPatientIdAndPayerIdOrderByCheckedAtDesc(patientId, payerId)
                : eligibilityChecks.findFirstByPatientIdOrderByCheckedAtDesc(patientId);

        return check.map(c -> toResponse(c, false));
    }

    private EligibilityCheckResponse toResponse(EligibilityCheck check, boolean isCacheHit) {
        String patientName = check.getPatient().getLastName() + ", " + check.getPatient().getFirstName();
        String memberId = check.getPolicy() != null ? check.getPolicy().getMemberId() : null;
        Long policyId = check.getPolicy() != null ? check.getPolicy().getId() : null;

        String source = check.getBenefitSource() != null ? check.getBenefitSource() : "SIMULATED_DEFAULT";
        String sourceLabel;
        if (isCacheHit) {
            sourceLabel = "CACHED SIMULATED RESPONSE";
        } else if ("SIMULATED_CUSTOM_DEMO_PROFILE".equals(source)) {
            sourceLabel = "SIMULATED — CUSTOM DEMO PROFILE";
        } else {
            sourceLabel = "SIMULATED — DEFAULT PROFILE";
        }

        Long cacheAgeSeconds = null;
        if (check.getCheckedAt() != null) {
            cacheAgeSeconds = Math.max(0, Duration.between(check.getCheckedAt(), OffsetDateTime.now()).toSeconds());
        }

        return new EligibilityCheckResponse(
                check.getId(),
                check.getPatient().getId(),
                patientName,
                check.getPatient().getMrn(),
                check.getPayer().getId(),
                check.getPayer().getName(),
                policyId,
                memberId,
                check.getServiceTypeCode(),
                check.isActive(),
                check.getCheckedAt(),
                check.getPlanBegin(),
                check.getPlanEnd(),
                check.getCopay(),
                check.getCoinsurancePercent(),
                check.getDeductibleTotal(),
                check.getDeductibleRemaining(),
                check.getOutOfPocketRemaining(),
                isCacheHit,
                source,
                sourceLabel,
                cacheAgeSeconds,
                check.getRawResponse()
        );
    }

    private EligibilityHistoryItem toHistoryItem(EligibilityCheck check) {
        String patientName = check.getPatient().getLastName() + ", " + check.getPatient().getFirstName();
        String memberId = check.getPolicy() != null ? check.getPolicy().getMemberId() : null;
        Long policyId = check.getPolicy() != null ? check.getPolicy().getId() : null;

        return new EligibilityHistoryItem(
                check.getId(),
                check.getPatient().getId(),
                patientName,
                check.getPayer().getId(),
                check.getPayer().getName(),
                policyId,
                memberId,
                check.getServiceTypeCode(),
                check.getServiceDate(),
                check.isActive(),
                check.getCheckedAt(),
                check.getCopay(),
                check.getCoinsurancePercent(),
                check.getDeductibleRemaining(),
                check.isCached(),
                check.getBenefitSource() != null ? check.getBenefitSource() : "SIMULATED_DEFAULT",
                check.getCheckedBy()
        );
    }
}

