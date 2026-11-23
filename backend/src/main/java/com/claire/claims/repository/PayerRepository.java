package com.claire.claims.repository;

import com.claire.claims.domain.Payer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface PayerRepository extends JpaRepository<Payer, Long> {
    Optional<Payer> findByPayerCode(String payerCode);
    boolean existsByPayerCode(String payerCode);

    Page<Payer> findByActive(boolean active, Pageable pageable);

    @Query("""
        SELECT p FROM Payer p WHERE
        (:active IS NULL OR p.active = :active) AND
        (:term IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', CAST(:term AS String), '%'))
          OR LOWER(p.payerCode) LIKE LOWER(CONCAT('%', CAST(:term AS String), '%')))
    """)
    Page<Payer> searchPayers(@Param("term") String term, @Param("active") Boolean active, Pageable pageable);
}
