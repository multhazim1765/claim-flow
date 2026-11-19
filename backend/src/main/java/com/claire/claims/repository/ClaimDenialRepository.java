package com.claire.claims.repository;

import com.claire.claims.domain.ClaimDenial;
import com.claire.claims.domain.DenialStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ClaimDenialRepository extends JpaRepository<ClaimDenial, Long>,
                                               JpaSpecificationExecutor<ClaimDenial> {

    @Override
    @EntityGraph(attributePaths = {"claim", "payer", "claim.patient"})
    Page<ClaimDenial> findAll(Specification<ClaimDenial> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"claim", "payer", "claim.patient"})
    Optional<ClaimDenial> findWithReferencesById(Long id);

    boolean existsByClaimIdAndStatusIn(Long claimId, Collection<DenialStatus> statuses);

    List<ClaimDenial> findByClaimIdOrderByCreatedAtDesc(Long claimId);

    @Query("SELECT COUNT(d) FROM ClaimDenial d WHERE d.status = :status")
    long countByStatus(@Param("status") DenialStatus status);

    @Query("SELECT COALESCE(SUM(d.amount), 0) FROM ClaimDenial d WHERE d.status = :status")
    BigDecimal sumAmountByStatus(@Param("status") DenialStatus status);

    @Query("SELECT d.denialCategory, COUNT(d), COALESCE(SUM(d.amount), 0) FROM ClaimDenial d WHERE d.status = :status GROUP BY d.denialCategory")
    List<Object[]> countAndAmountByCategoryForStatus(@Param("status") DenialStatus status);
}

