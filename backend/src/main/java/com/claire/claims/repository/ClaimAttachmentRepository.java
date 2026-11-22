package com.claire.claims.repository;

import com.claire.claims.domain.ClaimAttachment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ClaimAttachmentRepository extends JpaRepository<ClaimAttachment, Long> {

    List<ClaimAttachment> findByClaimIdAndAttachmentStatusOrderByUploadedAtDesc(Long claimId, String attachmentStatus);

    List<ClaimAttachment> findByClaimIdOrderByUploadedAtDesc(Long claimId);

    Optional<ClaimAttachment> findByIdAndClaimId(Long id, Long claimId);

    Optional<ClaimAttachment> findByStorageKey(String storageKey);

    @Query("SELECT a FROM ClaimAttachment a WHERE (CAST(:category AS string) IS NULL OR a.documentCategory = :category) " +
           "AND (CAST(:status AS string) IS NULL OR a.attachmentStatus = :status) " +
           "AND (CAST(:search AS string) IS NULL OR LOWER(a.originalFileName) LIKE :search OR LOWER(a.claim.claimNumber) LIKE :search)")
    Page<ClaimAttachment> findFiltered(
            @Param("category") String category,
            @Param("status") String status,
            @Param("search") String search,
            Pageable pageable
    );

    @Query("SELECT COUNT(a) FROM ClaimAttachment a WHERE a.claim.id = :claimId AND a.attachmentStatus = 'ACTIVE'")
    long countActiveByClaimId(@Param("claimId") Long claimId);

    @Query("SELECT COALESCE(SUM(a.fileSize), 0) FROM ClaimAttachment a WHERE a.claim.id = :claimId AND a.attachmentStatus = 'ACTIVE'")
    long sumActiveSizeByClaimId(@Param("claimId") Long claimId);
}
