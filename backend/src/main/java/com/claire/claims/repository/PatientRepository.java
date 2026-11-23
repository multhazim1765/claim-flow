package com.claire.claims.repository;

import com.claire.claims.domain.Patient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface PatientRepository extends JpaRepository<Patient, Long> {

    Optional<Patient> findByMrn(String mrn);

    boolean existsByMrn(String mrn);

    Page<Patient> findByActive(boolean active, Pageable pageable);

    /**
     * Derived query rather than JPQL with a nullable parameter: comparing a
     * bind parameter to NULL needs an explicit type hint on some dialects, and
     * this cannot get that wrong. The service branches on a blank search term.
     */
    Page<Patient> findByLastNameContainingIgnoreCaseOrFirstNameContainingIgnoreCaseOrMrnContainingIgnoreCase(
            String lastName, String firstName, String mrn, Pageable pageable);

    @Query("""
        SELECT p FROM Patient p WHERE
        (:active IS NULL OR p.active = :active) AND
        (:term IS NULL OR LOWER(p.lastName) LIKE LOWER(CONCAT('%', CAST(:term AS String), '%'))
          OR LOWER(p.firstName) LIKE LOWER(CONCAT('%', CAST(:term AS String), '%'))
          OR LOWER(p.mrn) LIKE LOWER(CONCAT('%', CAST(:term AS String), '%')))
    """)
    Page<Patient> searchPatients(@Param("term") String term, @Param("active") Boolean active, Pageable pageable);
}
