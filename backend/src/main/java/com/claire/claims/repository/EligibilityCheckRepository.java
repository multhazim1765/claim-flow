package com.claire.claims.repository;

import com.claire.claims.domain.EligibilityCheck;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface EligibilityCheckRepository extends JpaRepository<EligibilityCheck, Long> {

    /**
     * Checks if a recent verified check exists for patient + payer + service date
     * within the specified cache window (e.g. 24 hours).
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

