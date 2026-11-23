package com.claire.claims.repository;

import com.claire.claims.domain.EligibilityCheck;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface EligibilityCheckRepository extends JpaRepository<EligibilityCheck, Long> {

    /**
     * Enhanced multi-dimensional cache matching:
     * Requires exact match on patient, payer, policy, service date, service type code,
     * and cryptographic/composite cache fingerprint within the cache freshness window.
     */
    @Query("SELECT e FROM EligibilityCheck e WHERE e.patient.id = :patientId " +
           "AND e.payer.id = :payerId " +
           "AND ((:policyId IS NULL AND e.policy IS NULL) OR (e.policy.id = :policyId)) " +
           "AND e.serviceDate = :serviceDate " +
           "AND e.serviceTypeCode = :serviceTypeCode " +
           "AND e.cacheFingerprint = :cacheFingerprint " +
           "AND e.checkedAt > :checkedAtAfter " +
           "ORDER BY e.checkedAt DESC")
    List<EligibilityCheck> findMatchingCachedChecks(
            @Param("patientId") Long patientId,
            @Param("payerId") Long payerId,
            @Param("policyId") Long policyId,
            @Param("serviceDate") LocalDate serviceDate,
            @Param("serviceTypeCode") String serviceTypeCode,
            @Param("cacheFingerprint") String cacheFingerprint,
            @Param("checkedAtAfter") OffsetDateTime checkedAtAfter);

    /**
     * Legacy single-dimension cache lookup kept for backward compatibility.
     */
    Optional<EligibilityCheck> findFirstByPatientIdAndPayerIdAndServiceDateAndCheckedAtAfterOrderByCheckedAtDesc(
            Long patientId, Long payerId, LocalDate serviceDate, OffsetDateTime checkedAtAfter);

    /**
     * Complete audit trail of eligibility checks for a patient.
     */
    List<EligibilityCheck> findByPatientIdOrderByCheckedAtDesc(Long patientId);

    /**
     * Most recent verification for a patient and specific payer.
     */
    Optional<EligibilityCheck> findFirstByPatientIdAndPayerIdOrderByCheckedAtDesc(Long patientId, Long payerId);

    /**
     * Most recent verification for a patient across any payer.
     */
    Optional<EligibilityCheck> findFirstByPatientIdOrderByCheckedAtDesc(Long patientId);
}

