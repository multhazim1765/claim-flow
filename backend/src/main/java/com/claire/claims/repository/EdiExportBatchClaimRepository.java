package com.claire.claims.repository;

import com.claire.claims.domain.EdiExportBatchClaim;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EdiExportBatchClaimRepository extends JpaRepository<EdiExportBatchClaim, Long> {

    List<EdiExportBatchClaim> findByClaimId(Long claimId);

    @Query("SELECT ebc FROM EdiExportBatchClaim ebc WHERE ebc.claim.id = :claimId ORDER BY ebc.exportedAt DESC")
    List<EdiExportBatchClaim> findLatestByClaimId(@Param("claimId") Long claimId);

    boolean existsByClaimId(Long claimId);

    @Query("SELECT ebc.claim.id FROM EdiExportBatchClaim ebc WHERE ebc.claim.id IN :claimIds")
    List<Long> findAlreadyExportedClaimIds(@Param("claimIds") List<Long> claimIds);
}

