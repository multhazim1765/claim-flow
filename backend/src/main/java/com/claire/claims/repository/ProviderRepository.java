package com.claire.claims.repository;

import com.claire.claims.domain.Provider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface ProviderRepository extends JpaRepository<Provider, Long> {
    Optional<Provider> findByNpi(String npi);
    boolean existsByNpi(String npi);

    Page<Provider> findByActive(boolean active, Pageable pageable);

    @Query("""
        SELECT p FROM Provider p WHERE
        (:active IS NULL OR p.active = :active) AND
        (:term IS NULL OR LOWER(p.lastName) LIKE LOWER(CONCAT('%', CAST(:term AS String), '%'))
          OR LOWER(p.firstName) LIKE LOWER(CONCAT('%', CAST(:term AS String), '%'))
          OR LOWER(p.npi) LIKE LOWER(CONCAT('%', CAST(:term AS String), '%'))
          OR LOWER(COALESCE(p.specialty, '')) LIKE LOWER(CONCAT('%', CAST(:term AS String), '%')))
    """)
    Page<Provider> searchProviders(@Param("term") String term, @Param("active") Boolean active, Pageable pageable);
}
