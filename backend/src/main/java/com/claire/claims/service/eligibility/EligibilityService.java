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
     * Enforces a 24-hour cache per patient + payer + service date unless forceRefresh is requested.
     */
    public EligibilityCheckResponse checkEligibility(EligibilityCheckRequest request) {
        Patient patient = patients.findById(request.patientId())
                .orElseThrow(() -> new NotFoundException("Patient", request.patientId()));
        Payer payer = payers.findById(request.payerId())
                .orElseThrow(() -> new NotFoundException("Payer", request.payerId()));

        InsurancePolicy policy = policies.findByPatientIdOrderByPriorityAsc(patient.getId()).stream()
                .filter(p -> p.getPayer().getId().equals(payer.getId()))
                .findFirst()
                .orElse(null);

        String serviceTypeCode = request.serviceTypeCode() != null && !request.serviceTypeCode().isBlank()
                ? request.serviceTypeCode()
                : "30";

        // Check 24-hour cache
        if (!Boolean.TRUE.equals(request.forceRefresh())) {
            OffsetDateTime cacheThreshold = OffsetDateTime.now().minusHours(24);
            Optional<EligibilityCheck> cachedCheck = eligibilityChecks
                    .findFirstByPatientIdAndPayerIdAndServiceDateAndCheckedAtAfterOrderByCheckedAtDesc(
                            patient.getId(), payer.getId(), request.serviceDate(), cacheThreshold);

            if (cachedCheck.isPresent()) {
                return toResponse(cachedCheck.get(), true);
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

        EligibilityCheck saved = eligibilityChecks.save(check);
        return toResponse(saved, false);
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

        return new EligibilityCheckResponse(
                check.getId(),
                check.getPatient().getId(),
                patientName,
                check.getPatient().getMrn(),
                check.getPayer().getId(),
                check.getPayer().getName(),
                memberId,
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
                check.getRawResponse()
        );
    }

    private EligibilityHistoryItem toHistoryItem(EligibilityCheck check) {
        String patientName = check.getPatient().getLastName() + ", " + check.getPatient().getFirstName();
        return new EligibilityHistoryItem(
                check.getId(),
                check.getPatient().getId(),
                patientName,
                check.getPayer().getId(),
                check.getPayer().getName(),
                check.getServiceDate(),
                check.isActive(),
                check.getCheckedAt(),
                check.getCopay(),
                check.getCoinsurancePercent(),
                check.getDeductibleRemaining(),
                check.isCached(),
                check.getCheckedBy()
        );
    }
}

