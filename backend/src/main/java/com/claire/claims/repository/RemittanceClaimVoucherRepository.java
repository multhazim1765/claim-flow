package com.claire.claims.repository;

import com.claire.claims.domain.RemittanceClaimVoucher;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface RemittanceClaimVoucherRepository extends JpaRepository<RemittanceClaimVoucher, Long> {

    @EntityGraph(attributePaths = {"claim", "adjustments"})
    List<RemittanceClaimVoucher> findByRemittanceIdOrderBySequenceNoAsc(Long remittanceId);

    @EntityGraph(attributePaths = {"claim", "adjustments"})
    List<RemittanceClaimVoucher> findByClaimIdOrderByPostedAtDesc(Long claimId);

    boolean existsByRemittanceIdAndClaimNumberAndPayerIcnAndEntryType(
            Long remittanceId, String claimNumber, String payerIcn, String entryType);

    boolean existsByRemittanceIdAndClaimNumberAndEntryType(
            Long remittanceId, String claimNumber, String entryType);

    @EntityGraph(attributePaths = {"claim", "adjustments", "remittance"})
    Optional<RemittanceClaimVoucher> findWithDetailsById(Long id);
}

