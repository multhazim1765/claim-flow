package com.claire.claims.repository;

import com.claire.claims.domain.ClaimAppeal;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClaimAppealRepository extends JpaRepository<ClaimAppeal, Long> {

    @EntityGraph(attributePaths = {"claim", "denial", "claim.patient", "claim.payer"})
    List<ClaimAppeal> findByClaimIdOrderByLevelAsc(Long claimId);

    @EntityGraph(attributePaths = {"claim", "denial", "claim.patient", "claim.payer"})
    Optional<ClaimAppeal> findWithReferencesById(Long id);

    boolean existsByClaimIdAndOutcomeIsNull(Long claimId);

    Optional<ClaimAppeal> findTopByClaimIdOrderByLevelDesc(Long claimId);

    Optional<ClaimAppeal> findByClaimIdAndOutcomeIsNull(Long claimId);
}

