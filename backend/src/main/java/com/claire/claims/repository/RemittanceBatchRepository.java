package com.claire.claims.repository;

import com.claire.claims.domain.RemittanceBatch;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.time.LocalDate;
import java.util.Optional;

public interface RemittanceBatchRepository extends JpaRepository<RemittanceBatch, Long>,
                                                   JpaSpecificationExecutor<RemittanceBatch> {

    boolean existsByPayerIdAndCheckNumberAndCheckDate(Long payerId, String checkNumber, LocalDate checkDate);

    Optional<RemittanceBatch> findByBatchControlNumber(String batchControlNumber);

    @Override
    @EntityGraph(attributePaths = {"payer"})
    Page<RemittanceBatch> findAll(Specification<RemittanceBatch> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"payer"})
    Optional<RemittanceBatch> findWithDetailsById(Long id);
}

