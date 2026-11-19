package com.claire.claims.repository;

import com.claire.claims.domain.RemittanceClaimException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.List;

public interface RemittanceClaimExceptionRepository extends JpaRepository<RemittanceClaimException, Long>,
                                                            JpaSpecificationExecutor<RemittanceClaimException> {

    @EntityGraph(attributePaths = {"remittance"})
    List<RemittanceClaimException> findByRemittanceIdOrderByIdAsc(Long remittanceId);

    @Override
    @EntityGraph(attributePaths = {"remittance"})
    Page<RemittanceClaimException> findAll(Specification<RemittanceClaimException> spec, Pageable pageable);
}

